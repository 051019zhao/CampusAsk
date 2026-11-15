package com.campusask.campusask.rag;

import com.campusask.campusask.context.BaseContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

/** Keeps the existing Java API and authorization context while LangGraph runs in Python. */
@Service
public class LangGraphGateway {
    private final RagService rag;
    private final ObjectMapper json=new ObjectMapper();
    @Value("${agent.service.url:}") private String serviceUrl;
    @Value("${agent.shared-key:}") private String sharedKey;
    public LangGraphGateway(RagService rag) { this.rag=rag; }

    public Map<String,Object> ask(String question,String sessionId) {
        if(question==null || question.isBlank() || question.length()>2000)
            throw new IllegalArgumentException("问题需为 1～2000 字符");
        String session=rag.session(sessionId);
        if(serviceUrl==null || serviceUrl.isBlank() || sharedKey==null || sharedKey.length()<32)
            return fallback(question,session);
        try {
            URI uri=URI.create(serviceUrl);
            if(uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || ("http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.getHost()))))
                throw new IllegalArgumentException("Agent 服务地址必须为 HTTPS 或本机 HTTP");
            String username=BaseContext.getCurrentUsername();
            String owner=username==null || username.isBlank()?"GUEST":"USER:"+username;
            byte[] body=json.writeValueAsBytes(Map.of("question",question,"sessionId",session,"owner",owner));
            JsonHttp.Response response=JsonHttp.post(uri,body,Map.of("X-Agent-Key",sharedKey),100_000);
            if(response.status()!=200) throw new IllegalStateException("Agent 服务不可用");
            JsonNode data=json.readTree(response.body());
            String answer=data.path("answer").asText("");
            if(data.path("fallback").asBoolean() || answer.isBlank() || answer.length()>6000)
                return fallback(question,session);
            List<Map<String,Object>> citations=new ArrayList<>();
            for(JsonNode item:data.path("citations")) {
                if(citations.size()>=12) break;
                Map<String,Object> citation=new LinkedHashMap<>();
                for(String field:List.of("id","documentId","title","excerpt"))
                    citation.put(field,item.path(field).asText(""));
                citation.put("chunk",item.path("chunk").asInt());
                citation.put("score",item.path("score").asDouble());
                String url=item.path("url").asText("");
                if(!url.isBlank()) {
                    URI source=URI.create(url);
                    if("https".equalsIgnoreCase(source.getScheme()) && source.getHost()!=null && source.getUserInfo()==null)
                        citation.put("url",url);
                }
                citations.add(citation);
            }
            List<String> steps=new ArrayList<>();
            for(JsonNode step:data.path("agentSteps")) if(steps.size()<4) steps.add(step.asText(""));
            return Map.of("source","agent","answer",answer,"citations",citations,"agentSteps",steps,
                    "retrievalMode",data.path("retrievalMode").asText("lexical"),"memoryEnabled",false,"memorySaved",false);
        } catch(Exception ex) {
            org.slf4j.LoggerFactory.getLogger(LangGraphGateway.class).warn("langgraph_unavailable type={}",ex.getClass().getSimpleName());
            return fallback(question,session);
        }
    }
    private boolean isLoopback(String host) { return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host); }
    private Map<String,Object> fallback(String question,String session) {
        Map<String,Object> result=new LinkedHashMap<>(rag.ask(question,session,false));
        result.put("agentFallback",true);
        result.put("agentSteps",List.of());
        return result;
    }
}
