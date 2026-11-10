package com.campusask.campusask.config;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.controller.QaController;
import com.campusask.campusask.crawler.CrawlerController;
import com.campusask.campusask.rag.RagController;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Order(-10)
@RestControllerAdvice(assignableTypes={QaController.class,RagController.class,CrawlerController.class})
public class ApiExceptionHandler {
    private ResponseEntity<Result<Void>> error(HttpStatusCode status,String message) {
        Result<Void> body=Result.error(message); body.setCode(status.value()); return ResponseEntity.status(status).body(body);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> status(ResponseStatusException e) {return error(e.getStatusCode(),e.getReason());}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException e) {
        return error(HttpStatus.BAD_REQUEST,e.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> malformed() {return error(HttpStatus.BAD_REQUEST,"请求格式不正确");}
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> invalid(IllegalArgumentException e) {return error(HttpStatus.BAD_REQUEST,e.getMessage());}
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> tooLarge() {return error(HttpStatus.PAYLOAD_TOO_LARGE,"文件不能超过 5 MB");}
    @ExceptionHandler(org.springframework.web.bind.ServletRequestBindingException.class)
    public ResponseEntity<Result<Void>> missingParameter() {return error(HttpStatus.BAD_REQUEST,"缺少必要请求参数");}
    @ExceptionHandler({IllegalStateException.class,java.io.IOException.class})
    public ResponseEntity<Result<Void>> unavailable(Exception e) {return error(HttpStatus.SERVICE_UNAVAILABLE,"资料处理或外部服务暂不可用，请稍后重试");}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpected(Exception e) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("api_request_failed type={}",e.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR,"服务暂时异常，请凭请求编号联系管理员");
    }
}
