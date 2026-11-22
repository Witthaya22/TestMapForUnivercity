package th.ac.kmutnb.prachin.map.ui.khaoyai

enum class UserRole { VISITOR, ADMIN }

data class KhaoYaiSession(val username: String, val role: UserRole) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
}

/**
 * Who a sign-in becomes.
 *
 * shortcut: there is no account store yet - any username (even blank) signs in as a visitor
 * and "admin" in any letter case signs in as admin, with no password check. Replace with a
 * real authentication backend before the app is published.
 */
object KhaoYaiAuth {
    const val ADMIN_USERNAME = "admin"

    fun signIn(username: String): KhaoYaiSession {
        val name = username.trim()
        val role = if (name.equals(ADMIN_USERNAME, ignoreCase = true)) UserRole.ADMIN else UserRole.VISITOR
        return KhaoYaiSession(username = name, role = role)
    }
}
