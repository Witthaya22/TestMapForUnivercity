package th.ac.kmutnb.prachin.map.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.security.PasswordHasher
import th.ac.kmutnb.prachin.map.data.model.AccountForm
import th.ac.kmutnb.prachin.map.data.model.AccountProblem
import th.ac.kmutnb.prachin.map.data.model.AccountRules
import th.ac.kmutnb.prachin.map.data.model.Permission
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.data.model.UserRole

class AccountRulesTest {

    private val good = AccountForm("somchai_99", "Khaoyai2569", "Khaoyai2569", "สมชาย ใจดี", "081-234-5678", "somchai@mail.com", "ไทย")

    @Test
    fun `a complete registration has no problems`() {
        assertEquals(emptyList<AccountProblem>(), AccountRules.problems(good))
    }

    @Test
    fun `each field of the proposal's form is checked`() {
        assertTrue(AccountProblem.USERNAME_FORMAT in AccountRules.problems(good.copy(username = "ab")))
        assertTrue(AccountProblem.USERNAME_FORMAT in AccountRules.problems(good.copy(username = "มีภาษาไทย")))
        assertTrue(AccountProblem.PASSWORD_WEAK in AccountRules.problems(good.copy(password = "short1", confirmPassword = "short1")))
        assertTrue(AccountProblem.PASSWORD_WEAK in AccountRules.problems(good.copy(password = "onlyletters", confirmPassword = "onlyletters")))
        assertTrue(AccountProblem.PASSWORD_MISMATCH in AccountRules.problems(good.copy(confirmPassword = "Other1234")))
        assertTrue(AccountProblem.NAME_MISSING in AccountRules.problems(good.copy(fullName = " ")))
        assertTrue(AccountProblem.PHONE_FORMAT in AccountRules.problems(good.copy(phone = "12")))
        assertTrue(AccountProblem.EMAIL_FORMAT in AccountRules.problems(good.copy(email = "no-at-sign")))
        assertTrue(AccountProblem.NATIONALITY_MISSING in AccountRules.problems(good.copy(nationality = "")))
        // Editing a profile does not re-check the password.
        assertEquals(emptyList<AccountProblem>(), AccountRules.problems(good.copy(password = "", confirmPassword = ""), checkPassword = false))
    }

    @Test
    fun `foreign phone numbers with a country code are accepted`() {
        assertTrue(AccountRules.isPhone("+49 170 1234567"))
        assertFalse(AccountRules.isPhone("12+34567890"))
    }

    @Test
    fun `member ids carry the role, the Buddhist year and a running number`() {
        assertEquals("KY69-000123", AccountRules.memberId(UserRole.TOURIST, 2569, 123))
        assertEquals("OF69-000002", AccountRules.memberId(UserRole.OFFICER, 2569, 2))
        assertEquals("AD70-000001", AccountRules.memberId(UserRole.ADMIN, 2570, 1))
    }

    @Test
    fun `the next member id follows the highest in use, not the count`() {
        // KY69-000002..000007 were demo accounts and got deleted; 000008 is a real one left.
        assertEquals(9, AccountRules.nextSequence(listOf("KY69-000001", "KY69-000008")))
        assertEquals(1, AccountRules.nextSequence(emptyList()))
        assertEquals(4, AccountRules.nextSequence(listOf("KY69-000003", "broken")))
    }

    @Test
    fun `the OTP screen says where the code went without showing the whole address`() {
        assertEquals("s*****@mail.com", AccountRules.maskEmail("somchai@mail.com"))
        assertEquals("a@b.co", AccountRules.maskEmail("a@b.co"))
    }

    @Test
    fun `officers do what they were granted, admins everything, tourists nothing`() {
        fun account(role: UserRole, permissions: Set<Permission>) =
            UserAccount("i", "m", "u", "n", "p", "e", "th", role, permissions, 0L)
        val officer = account(UserRole.OFFICER, setOf(Permission.MANAGE_SOS))
        assertTrue(officer.can(Permission.MANAGE_SOS))
        assertFalse(officer.can(Permission.VIEW_REPORTS))
        assertTrue(account(UserRole.ADMIN, emptySet()).can(Permission.VIEW_REPORTS))
        assertFalse(account(UserRole.TOURIST, Permission.entries.toSet()).can(Permission.MANAGE_SOS))
        assertEquals(setOf(Permission.MANAGE_SOS, Permission.EDIT_TRAILS), Permission.fromIds(Permission.toIds(setOf(Permission.EDIT_TRAILS, Permission.MANAGE_SOS))))
    }

    @Test
    fun `the avatar letter skips a Thai leading vowel`() {
        assertEquals("จ", AccountRules.initial("เจ้าหน้าที่อุทยาน"))
        assertEquals("ส", AccountRules.initial("สมชาย ใจดี"))
        assertEquals("A", AccountRules.initial(" anna"))
        assertEquals("?", AccountRules.initial(""))
    }

    @Test
    fun `officers find a tourist by name, Tourist ID, username or phone`() {
        val t = UserAccount("i", "KY69-000042", "anna_s", "Anna Schmidt", "0812345678", "e", "de", UserRole.TOURIST, emptySet(), 0L)
        assertTrue(t.matches("anna"))
        assertTrue(t.matches(" 000042 "))
        assertTrue(t.matches("ANNA_S"))
        assertTrue(t.matches("081234"))
        assertFalse(t.matches("John"))
    }

    @Test
    fun `passwords are stored salted and checked in constant shape`() {
        val a = PasswordHasher.hash("Khaoyai2569")
        val b = PasswordHasher.hash("Khaoyai2569")
        assertNotEquals("the same password must not hash the same twice", a, b)
        assertTrue(PasswordHasher.verify("Khaoyai2569", a))
        assertFalse(PasswordHasher.verify("khaoyai2569", a))
        assertFalse(PasswordHasher.verify("Khaoyai2569", "garbage"))
        assertFalse(a.contains("Khaoyai2569"))
        val otp = PasswordHasher.otp()
        assertTrue(otp.length == 6 && otp.all(Char::isDigit))
    }
}
