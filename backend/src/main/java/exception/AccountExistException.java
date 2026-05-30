package exception;

/**
 * 账号已存在异常
 */
public class AccountExistException extends RuntimeException {
    public AccountExistException(String message) {
        super(message);
    }
}
