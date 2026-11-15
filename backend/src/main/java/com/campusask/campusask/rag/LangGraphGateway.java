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
    private final RagStore store;
    private final ObjectMapper json=new ObjectMapper();
    @Value("${agent.service.url:}") private String serviceUrl;
    @Value("${agent.shared-key:}") private String sharedKey;
    public LangGraphGateway(RagService rag,RagStore store) { this.rag=rag;this.store=store; }

    public Map<String,Object> ask(String question,String sessionId) {
        if(question==null || question.isBlank() || question.length()>2000)
            throw new IllegalArgumentException("问题需为 1～2000 字符");
        String session=rag.session(sessionId);
        String username=BaseContext.getCurrentUsername();
        String owner=username==null || username.isBlank()?"GUEST":"USER:"+username;
        String runId=UUID.randomUUID().toString();
        long started=System.nanoTime();
        if(serviceUrl==null || serviceUrl.isBlank() || sharedKey==null || sharedKey.length()<32)
            return audited(fallback(question,session),runId,owner,session,started,0,0);
        try {
            URI uri=URI.create(serviceUrl);
            if(uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || ("http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.getHost()))))
                throw new IllegalArgumentException("Agent 服务地址必须为 HTTPS 或本机 HTTP");
            byte[] body=json.writeValueAsBytes(Map.of("question",question,"sessionId",session,"owner",owner));
            JsonHttp.Response response=JsonHttp.post(uri,body,Map.of("X-Agent-Key",sharedKey),100_000);
            if(response.status()!=200) throw new IllegalStateException("Agent 服务不可用");
            JsonNode data=json.readTree(response.body());
            String answer=data.path("answer").asText("");
            if(data.path("fallback").asBoolean() || answer.isBlank() || answer.length()>6000)
                return audited(fallback(question,session),runId,owner,session,started,
                        data.path("modelTurns").asInt(0),data.path("toolCalls").asInt(0));
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
            if(owner.startsWith("USER:")) try { store.agentRemember(owner,session,question,answer); }
            catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(LangGraphGateway.class)
                    .warn("agent_history_save_failed type={}",ex.getClass().getSimpleName()); }
            return audited(Map.of("source","agent","answer",answer,"citations",citations,"agentSteps",steps,
                    "retrievalMode",data.path("retrievalMode").asText("lexical"),"memoryEnabled",false,"memorySaved",false),
                    runId,owner,session,started,data.path("modelTurns").asInt(0),data.path("toolCalls").asInt(0));
        } catch(Exception ex) {
            org.slf4j.LoggerFactory.getLogger(LangGraphGateway.class).warn("langgraph_unavailable type={}",ex.getClass().getSimpleName());
            return audited(fallback(question,session),runId,owner,session,started,0,0);
        }
    }
    private Map<String,Object> audited(Map<String,Object> result,String id,String owner,String session,long started,
                                       int modelTurns,int toolCalls) {
        Map<String,Object> output=new LinkedHashMap<>(result);
        output.put("agentRunId",id);
        String status="agent".equals(result.get("source"))?"VERIFIED":"FALLBACK";
        String mode=Objects.toString(result.get("retrievalMode"),"lexical");
        int citations=result.get("citations") instanceof List<?> items?items.size():0;
        long durationMs=Math.max(0,(System.nanoTime()-started)/1_000_000);
        try { store.recordAgentRun(id,owner,session,status,mode,citations,
                Math.max(0,Math.min(modelTurns,5)),Math.max(0,Math.min(toolCalls,6)),durationMs); }
        catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(LangGraphGateway.class)
                .warn("agent_run_audit_failed type={}",ex.getClass().getSimpleName()); }
        return output;
    }
    private boolean isLoopback(String host) { return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host); }
    private Map<String,Object> fallback(String question,String session) {
        Map<String,Object> result=new LinkedHashMap<>(rag.ask(question,session,false));
        result.put("agentFallback",true);
        result.put("agentSteps",List.of());
        return result;
    }
}
