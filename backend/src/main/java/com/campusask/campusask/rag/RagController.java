package com.campusask.campusask.rag;

import com.campusask.campusask.dto.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/qa/rag")
public class RagController {
    private final RagService rag;
    public RagController(RagService rag) { this.rag=rag; }
    @PostMapping("/documents")
    public Result<Map<String,Object>> upload(@RequestParam String sessionId, @RequestParam MultipartFile file) throws java.io.IOException {
        return Result.success(rag.upload(sessionId,file));
    }
    @GetMapping("/documents")
    public Result<List<Map<String,Object>>> documents(@RequestParam String sessionId) {
        return Result.success(rag.documents(sessionId));
    }
    @DeleteMapping("/documents/{id}")
    public Result<Boolean> delete(@RequestParam String sessionId,@PathVariable String id) {
        return Result.success(rag.delete(sessionId,id));
    }
    @DeleteMapping("/memory")
    public Result<String> forget() { rag.forget(); return Result.success("历史记忆已删除"); }
}
