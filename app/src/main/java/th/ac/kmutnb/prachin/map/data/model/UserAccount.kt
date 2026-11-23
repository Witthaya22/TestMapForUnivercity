package th.ac.kmutnb.prachin.map.data.model

import th.ac.kmutnb.prachin.map.R

/** The three roles of the proposal (§2.3.1–2.3.3). */
enum class UserRole(val id: String, val labelRes: Int) {
    TOURIST("tourist", R.string.role_tourist),
    OFFICER("officer", R.string.role_officer),
    ADMIN("admin", R.string.role_admin);

    companion object {
        fun fromId(id: String?): UserRole = entries.firstOrNull { it.id == id } ?: TOURIST
    }
}

/**
 * What a park officer may do, granted per officer by an administrator (§2.3.3.2).
 * An administrator always has all of them; a tourist has none.
 */
enum class Permission(val id: String, val labelRes: Int) {
    MANAGE_SOS("manage_sos", R.string.permission_manage_sos),
    VIEW_TOURISTS("view_tourists", R.string.permission_view_tourists),
    EDIT_TRAILS("edit_trails", R.string.permission_edit_trails),
    VIEW_REPORTS("view_reports", R.string.permission_view_reports);

    companion object {
        fun fromIds(text: String): Set<Permission> =
            text.split(',').mapNotNull { id -> entries.firstOrNull { it.id == id.trim() } }.toSet()

        fun toIds(set: Set<Permission>): String = entries.filter { it in set }.joinToString(",") { it.id }
    }
}

data class UserAccount(
    val id: String,
    /** Tourist ID for tourists (§2.3.1.1), staff number for officers and admins. */
    val memberId: String,
    val username: String,
    val fullName: String,
    val phone: String,
    val email: String,
    val nationality: String,
    val role: UserRole,
    val permissions: Set<Permission>,
    val createdAt: Long,
    val isActive: Boolean = true,
    /** Seeded sample accounts, so the officer and report screens have something to show. */
    val isDemo: Boolean = false,
) {
    val isStaff: Boolean get() = role != UserRole.TOURIST
    val isAdmin: Boolean get() = role == UserRole.ADMIN

    fun can(permission: Permission): Boolean = when (role) {
        UserRole.ADMIN -> true
        UserRole.OFFICER -> permission in permissions
        UserRole.TOURIST -> false
    }
}

/** What a registration or profile form holds, as typed. */
data class AccountForm(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val nationality: String = "",
) : java.io.Serializable

enum class AccountProblem(val messageRes: Int) {
    USERNAME_FORMAT(R.string.account_error_username),
    USERNAME_TAKEN(R.string.account_error_username_taken),
    PASSWORD_WEAK(R.string.account_error_password),
    PASSWORD_MISMATCH(R.string.signup_password_mismatch),
    NAME_MISSING(R.string.account_error_name),
    PHONE_FORMAT(R.string.account_error_phone),
    EMAIL_FORMAT(R.string.account_error_email),
    EMAIL_TAKEN(R.string.account_error_email_taken),
    NATIONALITY_MISSING(R.string.account_error_nationality),
}

/** The rules a form must pass, pure so they are tested without a database. */
object AccountRules {
    private val USERNAME = Regex("^[A-Za-z0-9_.]{4,20}$")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    /** 8+ characters with at least one letter and one digit. */
    fun isStrongPassword(password: String): Boolean =
        password.length >= 8 && password.any { it.isLetter() } && password.any { it.isDigit() }

    /** Thai mobile and landline numbers, or +country numbers for foreign visitors. */
    fun normalisePhone(phone: String): String = phone.filter { it.isDigit() || it == '+' }

    fun isPhone(phone: String): Boolean {
        val p = normalisePhone(phone)
        return p.length in 9..15 && p.count { it == '+' } <= 1 && (p.indexOf('+') <= 0)
    }

    /**
     * Problems with a form; [checkPassword] false when editing a profile, where the
     * password is changed separately.
     */
    fun problems(form: AccountForm, checkPassword: Boolean = true): List<AccountProblem> = buildList {
        if (!USERNAME.matches(form.username.trim())) add(AccountProblem.USERNAME_FORMAT)
        if (checkPassword) {
            if (!isStrongPassword(form.password)) add(AccountProblem.PASSWORD_WEAK)
            if (form.password != form.confirmPassword) add(AccountProblem.PASSWORD_MISMATCH)
        }
        if (form.fullName.isBlank()) add(AccountProblem.NAME_MISSING)
        if (!isPhone(form.phone)) add(AccountProblem.PHONE_FORMAT)
        if (!EMAIL.matches(form.email.trim())) add(AccountProblem.EMAIL_FORMAT)
        if (form.nationality.isBlank()) add(AccountProblem.NATIONALITY_MISSING)
    }

    /**
     * `KY69-000123`: KY, the Buddhist year's last two digits, the running number.
     * Staff get `OF` and `AD` the same way.
     */
    fun memberId(role: UserRole, buddhistYear: Int, sequence: Int): String {
        val prefix = when (role) {
            UserRole.TOURIST -> "KY"
            UserRole.OFFICER -> "OF"
            UserRole.ADMIN -> "AD"
        }
        return "%s%02d-%06d".format(prefix, buddhistYear % 100, sequence)
    }

    /**
     * One past the highest running number in use. Not the count plus one: once accounts are
     * deleted (the demo ones are, in a batch) the count falls and would hand out an id that
     * a remaining account still has.
     */
    fun nextSequence(existingIds: List<String>): Int =
        (existingIds.mapNotNull { it.substringAfterLast('-').toIntOrNull() }.maxOrNull() ?: 0) + 1

    /** `t***@gmail.com`, so the OTP screen says where the code went without showing it all. */
    fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 1) return email
        return email.first() + "*".repeat((at - 1).coerceAtMost(5)) + email.substring(at)
    }
}
