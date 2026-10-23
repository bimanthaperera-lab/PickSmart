package lk.picksmart.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFormsTest {

    @Test
    fun `email must be present and look like an address`() {
        assertEquals(AuthFieldError.EMAIL_REQUIRED, emailError("  "))
        assertEquals(AuthFieldError.EMAIL_INVALID, emailError("rider"))
        assertEquals(AuthFieldError.EMAIL_INVALID, emailError("rider@mail"))
        assertEquals(AuthFieldError.EMAIL_INVALID, emailError("ri der@mail.lk"))
        assertNull(emailError("rider@mail.lk"))
        assertNull(emailError("  rider@mail.lk  "))
    }

    @Test
    fun `mobile number is optional but must be a real number when given`() {
        assertNull(mobileError(""))
        assertNull(mobileError("077 123 4567"))
        assertNull(mobileError("+94 77 123 4567"))
        assertNull(mobileError("(077) 123-4567"))
        assertEquals(AuthFieldError.MOBILE_INVALID, mobileError("12345"))
        assertEquals(AuthFieldError.MOBILE_INVALID, mobileError("077 123 abcd"))
        assertEquals(AuthFieldError.MOBILE_INVALID, mobileError("1234567890123456"))
    }

    @Test
    fun `mobile number is saved tidied, and an empty one is not saved`() {
        assertEquals("+94 77 123 4567", cleanMobile("  +94  77 123   4567 "))
        assertNull(cleanMobile("   "))
    }

    @Test
    fun `login needs an email and a password`() {
        assertEquals(
            mapOf(AuthField.EMAIL to AuthFieldError.EMAIL_REQUIRED, AuthField.PASSWORD to AuthFieldError.PASSWORD_REQUIRED),
            LoginForm().errors(),
        )
        assertTrue(LoginForm("rider@mail.lk", "x").errors().isEmpty())
    }

    @Test
    fun `register checks the name, password length and that both passwords match`() {
        val good = RegisterForm("Kasun Perera", "kasun@mail.lk", "", "longenough", "longenough")
        assertTrue(good.errors().isEmpty())
        assertEquals(AuthFieldError.NAME_REQUIRED, good.copy(name = " ").errors()[AuthField.NAME])
        assertEquals(AuthFieldError.PASSWORD_TOO_SHORT, good.copy(password = "short", confirmPassword = "short").errors()[AuthField.PASSWORD])
        assertEquals(AuthFieldError.PASSWORDS_DIFFER, good.copy(confirmPassword = "different1").errors()[AuthField.CONFIRM_PASSWORD])
        assertEquals(AuthFieldError.MOBILE_INVALID, good.copy(mobile = "123").errors()[AuthField.MOBILE])
    }

    @Test
    fun `a too-short password is reported without also saying they differ`() {
        val errors = RegisterForm("A", "a@b.lk", "", "short", "other").errors()
        assertEquals(AuthFieldError.PASSWORD_TOO_SHORT, errors[AuthField.PASSWORD])
        assertNull(errors[AuthField.CONFIRM_PASSWORD])
    }

    @Test
    fun `initials come from the first two words of the name, else the email`() {
        assertEquals("KP", initialsOf("Kasun Perera", "k@mail.lk"))
        assertEquals("KS", initialsOf("kasun  silva perera", null))
        assertEquals("N", initialsOf("Nimal", null))
        assertEquals("R", initialsOf(null, "rider@mail.lk"))
        assertEquals("R", initialsOf("  ", "rider@mail.lk"))
        assertNull(initialsOf(null, null))
    }
}
