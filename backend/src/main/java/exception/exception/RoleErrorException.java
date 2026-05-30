package exception.exception;

/**
 * 身份错误异常
 */
public class RoleErrorException extends RuntimeException {
    public RoleErrorException(String message) {
        super(message);
    }
}
