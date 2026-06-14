package exception;


import com.campusask.campusask.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler{
    /**
     * 场景：当前端传来的 JSON 格式不符合 @NotBlank, @Size, @Pattern 等规则时触发
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.warn("【参数校验异常-JSON】触发拦截：{}", ex.getMessage());

        // 把所有字段校验失败的提示信息（例如：密码不能为空、昵称长度超限）用逗号拼接起来
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return Result.error(errorMsg); // 返回标准的 {code: 0/400, msg: "密码不能为空", data: null}
    }

    /**
     * 场景：普通表单提交或 URL 传参校验失败时触发
     */
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException ex) {
        log.warn("【参数校验异常-Form】触发拦截：{}", ex.getMessage());

        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return Result.error(errorMsg);
    }

    /**拦截手动 throw 出来的业务异常
     * 场景：如密码连续错误5次被锁定、用户名不存在、旧密码输入错误等
     * 提示：如果你们项目定义了基类业务异常（如 BaseException），把 IllegalArgumentException.class 换成你们的自定义基类即可
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Result<String> handleBusinessException(RuntimeException ex) {
        log.warn("【业务逻辑异常】触发拦截，原因: {}", ex.getMessage());
        // 直接抓取你 throw IllegalArgumentException("用户不存在") 里的那句话吐给前端
        return Result.error(ex.getMessage());
    }

    /**防止抛出恶心的 500 空指针或 SQL 报错页面
     * 场景：NullPointerException, SQLException 等未知系统翻车事故
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception ex) {
        // 在后端控制台打印出最地道的错误堆栈，方便你跟队友排雷
        log.error("【系统未知异常】炸网警告！堆栈信息如下：", ex);
        // 绝对不向前端暴露任何一丁点 SQL 语句或 Java 代码行数，用一句温柔的话进行物理保底
        return Result.error("系统开小差啦，请稍后再试或联系管理员！");
    }
}
