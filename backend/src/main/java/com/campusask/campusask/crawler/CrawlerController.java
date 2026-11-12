package com.campusask.campusask.crawler;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.dto.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/crawler")
public class CrawlerController {
    public record Review(@NotBlank String fingerprint,@NotBlank @Size(max=500) String title,
                         @NotBlank @Size(max=4000) String content,@NotBlank @Size(max=50) String category) {}
    public record Reject(@NotBlank String fingerprint) {}
    private final CrawlerService crawler;
    private final CrawlStore store;
    public CrawlerController(CrawlerService crawler,CrawlStore store) { this.crawler=crawler;this.store=store; }
    @GetMapping("/status") public Result<Map<String,Object>> status() { return Result.success(crawler.status()); }
    @PostMapping("/run") public Result<Map<String,String>> run() { return Result.success(Map.of("runId",crawler.trigger())); }
    @GetMapping("/candidates") public Result<Map<String,Object>> list(@RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="1") int page) {return Result.success(store.candidates(status,page));}
    @PostMapping("/candidates/{id}/approve") public Result<Long> approve(@PathVariable String id,@Valid @RequestBody Review body) {
        return Result.success(store.approve(id,body.fingerprint(),body.title(),body.content(),body.category(),BaseContext.getCurrentId()));
    }
    @PostMapping("/candidates/{id}/reject") public Result<String> reject(@PathVariable String id,@Valid @RequestBody Reject body) {
        store.reject(id,body.fingerprint(),BaseContext.getCurrentId()); return Result.success("已拒绝本版本");
    }
}
