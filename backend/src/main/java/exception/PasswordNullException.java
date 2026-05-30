package exception;

/**
 * 空密码异常
 */
public class PasswordNullException extends RuntimeException {
    public PasswordNullException(String message) {
        super(message);
    }
}
