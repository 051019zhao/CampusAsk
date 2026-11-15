package com.campusask.campusask.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Private callbacks for the Python LangGraph runtime; never expose this route publicly. */
@RestController
@RequestMapping("/internal/agent")
public class AgentBridgeController {
    public record ModelRequest(List<Object> messages,List<Map<String,Object>> tools) {}
    public record SearchRequest(String owner,String sessionId,String query) {}
    public record DocumentsRequest(String owner,String sessionId) {}
    private final RagService rag;
    private final RagStore store;
    private final RagModelClient model;
    @Value("${agent.shared-key:}") private String sharedKey;
    public AgentBridgeController(RagService rag,RagStore store,RagModelClient model) {
        this.rag=rag;this.store=store;this.model=model;
    }
    private void authorize(String supplied) {
        if(sharedKey==null || sharedKey.length()<32 || supplied==null
                || !MessageDigest.isEqual(sharedKey.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Agent 服务认证失败");
    }
    @PostMapping("/model")
    public Map<String,Object> model(@RequestHeader(value="X-Agent-Key",required=false) String key,@RequestBody ModelRequest request) {
        authorize(key);
        if(request.messages()==null || request.tools()==null) throw new IllegalArgumentException("模型请求无效");
        return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(model.agentTurn(request.messages(),request.tools()),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
    }
    @PostMapping("/search")
    public RagService.SearchResult search(@RequestHeader(value="X-Agent-Key",required=false) String key,@RequestBody SearchRequest request) {
        authorize(key);
        return rag.searchForOwner(request.query(),request.owner(),request.sessionId());
    }
    @PostMapping("/documents")
    public List<Map<String,Object>> documents(@RequestHeader(value="X-Agent-Key",required=false) String key,@RequestBody DocumentsRequest request) {
        authorize(key);
        if(request.owner()==null || !request.owner().matches("USER:[^\\p{Cntrl}]{1,120}"))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请先登录");
        return store.documents(request.owner(),rag.session(request.sessionId()));
    }
}
