package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import th.ac.kmutnb.prachin.map.core.security.PasswordHasher
import th.ac.kmutnb.prachin.map.data.local.UserAccountDao
import th.ac.kmutnb.prachin.map.data.local.UserAccountEntity
import th.ac.kmutnb.prachin.map.data.local.toAccount
import th.ac.kmutnb.prachin.map.data.model.AccountForm
import th.ac.kmutnb.prachin.map.data.model.AccountProblem
import th.ac.kmutnb.prachin.map.data.model.AccountRules
import th.ac.kmutnb.prachin.map.data.model.Permission
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.data.model.UserRole
import th.ac.kmutnb.prachin.map.data.prefs.AppPreferences
import java.util.Calendar
import java.util.UUID

sealed interface SignInResult {
    data class Success(val account: UserAccount) : SignInResult
    data object NoSuchUser : SignInResult
    data object WrongPassword : SignInResult
    data object Disabled : SignInResult
}

sealed interface RegisterResult {
    data class Success(val account: UserAccount) : RegisterResult
    data class Invalid(val problems: List<AccountProblem>) : RegisterResult
}

/** A reset code "sent" by email. With no mail server yet the screen shows [code] itself. */
data class OtpRequest(val email: String, val maskedEmail: String, val code: String, val expiresAt: Long)

/**
 * Accounts, sign-in and the signed-in session (§2.3.1.1, §2.3.2.1, §2.3.3.1-2).
 *
 * shortcut: everything lives on this phone and the OTP is shown on screen instead of
 * emailed; both move to the backend later, behind the same functions.
 */
class AccountRepository(
    private val dao: UserAccountDao,
    private val preferences: AppPreferences,
    scope: CoroutineScope,
) {
    private val seedLock = Mutex()
    private val otps = HashMap<String, OtpRequest>()

    private val _currentUserId = MutableStateFlow<String?>(null)

    /** Who is signed in, followed live: an admin disabling or editing them shows at once. */
    val currentUser: StateFlow<UserAccount?> =
        combine(_currentUserId, dao.observeAll()) { id, all -> all.firstOrNull { it.id == id }?.toAccount() }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val accounts: Flow<List<UserAccount>> = dao.observeAll().map { list -> list.map { it.toAccount() } }

    /** Restores the session saved at the last sign-in; null when signed out. */
    suspend fun restoreSession(): UserAccount? {
        ensureSeeded()
        val id = preferences.sessionUserId.first() ?: return null
        val account = dao.find(id)?.toAccount()?.takeIf { it.isActive }
        _currentUserId.value = account?.id
        if (account == null) preferences.setSessionUserId(null)
        return account
    }

    suspend fun signIn(username: String, password: String): SignInResult {
        ensureSeeded()
        val entity = dao.findByUsername(username.trim()) ?: return SignInResult.NoSuchUser
        if (!PasswordHasher.verify(password, entity.passwordHash)) return SignInResult.WrongPassword
        if (!entity.isActive) return SignInResult.Disabled
        _currentUserId.value = entity.id
        preferences.setSessionUserId(entity.id)
        return SignInResult.Success(entity.toAccount())
    }

    /** Clears the session (§2.3.1.1 "ล้าง Session"). */
    suspend fun signOut() {
        _currentUserId.value = null
        preferences.setSessionUserId(null)
    }

    /** A tourist signing up; they are signed in straight after. */
    suspend fun register(form: AccountForm): RegisterResult {
        val result = create(form, UserRole.TOURIST, emptySet())
        if (result is RegisterResult.Success) {
            _currentUserId.value = result.account.id
            preferences.setSessionUserId(result.account.id)
        }
        return result
    }

    /** An administrator adding any kind of account (§2.3.3.2). */
    suspend fun create(form: AccountForm, role: UserRole, permissions: Set<Permission>, isDemo: Boolean = false): RegisterResult {
        val problems = AccountRules.problems(form).toMutableList()
        if (dao.findByUsername(form.username.trim()) != null) problems += AccountProblem.USERNAME_TAKEN
        if (dao.findByEmail(form.email.trim()) != null) problems += AccountProblem.EMAIL_TAKEN
        if (problems.isNotEmpty()) return RegisterResult.Invalid(problems)
        val entity = UserAccountEntity(
            id = "user_${UUID.randomUUID()}",
            memberId = AccountRules.memberId(role, buddhistYear(), dao.countRole(role.id) + 1),
            username = form.username.trim(),
            passwordHash = PasswordHasher.hash(form.password),
            fullName = form.fullName.trim(),
            phone = AccountRules.normalisePhone(form.phone),
            email = form.email.trim(),
            nationality = form.nationality.trim(),
            role = role.id,
            permissions = Permission.toIds(permissions),
            createdAt = System.currentTimeMillis(),
            isActive = true,
            isDemo = isDemo,
        )
        dao.insert(entity)
        return RegisterResult.Success(entity.toAccount())
    }

    /** Profile fields only; username, role and password are changed elsewhere. */
    suspend fun updateProfile(id: String, form: AccountForm): List<AccountProblem> {
        val existing = dao.find(id) ?: return emptyList()
        val problems = AccountRules.problems(form.copy(username = existing.username), checkPassword = false).toMutableList()
        dao.findByEmail(form.email.trim())?.let { if (it.id != id) problems += AccountProblem.EMAIL_TAKEN }
        if (problems.isNotEmpty()) return problems
        dao.upsert(
            existing.copy(
                fullName = form.fullName.trim(),
                phone = AccountRules.normalisePhone(form.phone),
                email = form.email.trim(),
                nationality = form.nationality.trim(),
            ),
        )
        return emptyList()
    }

    /** Role, officer permissions and whether the account may sign in (§2.3.3.2). */
    suspend fun updateAccess(id: String, role: UserRole, permissions: Set<Permission>, isActive: Boolean) {
        val existing = dao.find(id) ?: return
        dao.upsert(existing.copy(role = role.id, permissions = Permission.toIds(permissions), isActive = isActive))
    }

    suspend fun changePassword(id: String, current: String, new: String): Boolean {
        val existing = dao.find(id) ?: return false
        if (!PasswordHasher.verify(current, existing.passwordHash) || !AccountRules.isStrongPassword(new)) return false
        dao.setPassword(id, PasswordHasher.hash(new))
        return true
    }

    /** An administrator setting a new password for someone who cannot reset their own. */
    suspend fun adminSetPassword(id: String, new: String): Boolean {
        if (!AccountRules.isStrongPassword(new)) return false
        dao.setPassword(id, PasswordHasher.hash(new))
        return true
    }

    suspend fun deleteDemo() = dao.deleteDemo()

    suspend fun delete(id: String) {
        dao.delete(id)
        if (_currentUserId.value == id) signOut()
    }

    // ----------------------------------------------------------------------------------
    // Forgotten password (§2.3.1.1, §2.3.2.1)
    // ----------------------------------------------------------------------------------

    /** Null when no account has that email - the screen still says "sent", to not leak who is registered. */
    suspend fun requestOtp(email: String, now: Long = System.currentTimeMillis()): OtpRequest? {
        ensureSeeded()
        val entity = dao.findByEmail(email.trim()) ?: return null
        val request = OtpRequest(entity.email, AccountRules.maskEmail(entity.email), PasswordHasher.otp(), now + OTP_LIFETIME_MS)
        otps[entity.email.lowercase()] = request
        return request
    }

    enum class ResetResult { OK, WRONG_CODE, EXPIRED, WEAK_PASSWORD }

    suspend fun resetWithOtp(email: String, code: String, newPassword: String, now: Long = System.currentTimeMillis()): ResetResult {
        val request = otps[email.trim().lowercase()] ?: return ResetResult.WRONG_CODE
        if (now > request.expiresAt) return ResetResult.EXPIRED
        if (request.code != code.trim()) return ResetResult.WRONG_CODE
        if (!AccountRules.isStrongPassword(newPassword)) return ResetResult.WEAK_PASSWORD
        val entity = dao.findByEmail(request.email) ?: return ResetResult.WRONG_CODE
        dao.setPassword(entity.id, PasswordHasher.hash(newPassword))
        otps.remove(email.trim().lowercase())
        return ResetResult.OK
    }

    // ----------------------------------------------------------------------------------

    /**
     * The built-in admin and officer accounts, so a fresh install can be run at all:
     * `admin` / `Admin1234` and `officer` / `Officer1234`. Shown on the sign-in screen as
     * demo accounts; change or delete them before real use.
     */
    suspend fun ensureSeeded() = seedLock.withLock {
        if (dao.count() > 0) return@withLock
        create(
            AccountForm("admin", "Admin1234", "Admin1234", "ผู้ดูแลระบบ", "0860926527", "admin@khaoyai.local", "ไทย"),
            UserRole.ADMIN, Permission.entries.toSet(),
        )
        create(
            AccountForm("officer", "Officer1234", "Officer1234", "เจ้าหน้าที่อุทยาน (ตัวอย่าง)", "0860926529", "officer@khaoyai.local", "ไทย"),
            UserRole.OFFICER, Permission.entries.toSet(),
        )
        create(
            AccountForm("tourist", "Tourist1234", "Tourist1234", "นักท่องเที่ยว ทดลอง", "0812345678", "tourist@khaoyai.local", "ไทย"),
            UserRole.TOURIST, emptySet(),
        )
    }

    private fun buddhistYear(): Int = Calendar.getInstance().get(Calendar.YEAR) + 543

    private companion object {
        const val OTP_LIFETIME_MS = 10 * 60 * 1000L
    }
}
