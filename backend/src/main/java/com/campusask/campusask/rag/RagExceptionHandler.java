package com.campusask.campusask.rag;

import com.campusask.campusask.controller.QaController;
import com.campusask.campusask.dto.Result;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(0)
@RestControllerAdvice(assignableTypes={RagController.class,QaController.class})
public class RagExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Result.error(ex.getMessage()));
    }
    @ExceptionHandler({IllegalStateException.class,java.io.IOException.class})
    public ResponseEntity<Result<Void>> unavailable(Exception ex) {
        Result<Void> body=Result.error("资料处理或模型服务暂不可用，请检查配置后重试");
        body.setCode(503);
        return ResponseEntity.status(503).body(body);
    }
}
