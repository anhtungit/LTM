package vn.edu.ltm.chat.application;

import org.junit.jupiter.api.Test;
import vn.edu.ltm.chat.domain.UserProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginServiceTest {
    private final LoginService service = new LoginService();

    @Test
    void acceptsValidEmailAndNonEmptyDemoPassword() {
        UserProfile user = service.login("minh.nguyen@example.com", "demo".toCharArray());

        assertEquals("minh.nguyen@example.com", user.email());
        assertEquals("Minh Nguyen", user.displayName());
        assertEquals("MN", user.initials());
    }

    @Test
    void rejectsMalformedEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> service.login("not-an-email", "demo".toCharArray()));
    }

    @Test
    void rejectsEmptyPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> service.login("minh@example.com", new char[0]));
    }
}
