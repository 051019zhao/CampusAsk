package exception;

/**
 * 账号格式异常
 */
public class AccountFormatException extends RuntimeException {
    public AccountFormatException(String message) {
        super(message);
    }
}
