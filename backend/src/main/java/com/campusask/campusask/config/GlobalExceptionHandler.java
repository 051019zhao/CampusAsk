package com.campusask.campusask.config;

import com.campusask.campusask.dto.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理校验错误和其他异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach((error) -> {
            String fieldName = error.getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        Result<Map<String, String>> result = new Result<>();
        result.setCode(400);
        result.setMessage("参数校验失败");
        result.setData(errors);
        return result;
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<String> handleMissingParams(MissingServletRequestParameterException ex) {
        Result<String> result = new Result<>();
        result.setCode(400);
        result.setMessage("缺少必填参数：" + ex.getParameterName());
        result.setData(null);
        return result;
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException ex) {
        Result<String> result = new Result<>();
        result.setCode(400);
        result.setMessage(ex.getMessage());
        result.setData(null);
        return result;
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception ex) {
        Result<String> result = new Result<>();
        result.setCode(500);
        result.setMessage("服务器内部错误");
        result.setData(null);
        return result;
    }
}