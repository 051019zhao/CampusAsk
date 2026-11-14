package com.campusask.campusask.rag;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.mapper.KnowledgeMapper;
import com.campusask.campusask.entity.Knowledge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class RagService {
    private final RagStore store;
    private final RagModelClient model;
    private final DocumentLoader loader;
    private final KnowledgeMapper knowledge;
    private final ObjectMapper json = new ObjectMapper();
    private final HybridRetriever retriever=new HybridRetriever();
    @Autowired(required=false) private MilvusIndex milvus;
    private volatile long syncedAt;
    public RagService(RagStore store, RagModelClient model, DocumentLoader loader, KnowledgeMapper knowledge) {
        this.store=store; this.model=model; this.loader=loader; this.knowledge=knowledge;
    }
    public String owner() {
        String username=BaseContext.getCurrentUsername();
        if (username==null || username.isBlank()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"请先登录，再使用资料问答与长时记忆");
        return "USER:" + username;
    }
    public String session(String id) {
        if (id==null || !id.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("会话 ID 无效");
        return id;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String encode(double[] values) {
        try { return json.writeValueAsString(values); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private double[] decode(String values) {
        try { return json.readValue(values,double[].class); } catch (Exception e) { return new double[0]; }
    }
    private List<RagStore.Chunk> index(String id, String title, String text) {
        List<RagStore.Chunk> result=new ArrayList<>();
        for (String chunk : TextChunks.split(text)) {
            int i=result.size()+1;
            result.add(new RagStore.Chunk(UUID.randomUUID().toString(),id,title,i,chunk,encode(model.embed(chunk)),model.embeddingVersion()));
        }
        return result;
    }
    public Map<String,Object> upload(String sessionId, MultipartFile file) throws java.io.IOException {
        String owner=owner(), session=session(sessionId);
        if (!model.embeddingsEnabled()) throw new IllegalArgumentException("请先配置 RAG 向量模型，再导入资料");
        String text=loader.load(file);
        String title=Objects.toString(file.getOriginalFilename(),"资料").replaceAll(".*[/\\\\]","");
        if (title.length()>255) title=title.substring(title.length()-255);
        String fingerprint=hash(text+model.embeddingVersion());
        String id=hash(owner+"|"+session+"|"+title+"|"+fingerprint);
        if (store.unchanged(id,fingerprint)) return Map.of("id",id,"title",title,"duplicate",true);
        if (store.documents(owner,session).size()>=10) throw new IllegalArgumentException("每个会话最多导入 10 份资料，请先移除旧资料");
        List<RagStore.Chunk> chunks=index(id,title,text);
        store.replace(id,owner,session,title,fingerprint,chunks);
        return Map.of("id",id,"title",title,"chunks",chunks.size(),"duplicate",false);
    }
    private String knowledgeText(Knowledge item) {
        StringBuilder text = new StringBuilder(Objects.toString(item.getTitle(), ""));
        if (item.getCategory()!=null && !item.getCategory().isBlank()) text.append("\n分类：").append(item.getCategory());
        if (item.getKeywords()!=null && !item.getKeywords().isBlank()) text.append("\n关键词：").append(item.getKeywords());
        return text.append("\n").append(Objects.toString(item.getContent(), "")).toString();
    }
    private synchronized void syncKnowledge() {
        if (System.currentTimeMillis()-syncedAt<30_000) return;
        Map<String,String> known=store.publicFingerprints();
        Set<String> ids=new HashSet<>();
        for (Knowledge k : knowledge.listAll()) {
            String id="K"+k.getId(); ids.add(id);
            String text=knowledgeText(k);
            String fingerprint=hash(text+model.embeddingVersion());
            if (!fingerprint.equals(known.get(id))) store.replace(id,"PUBLIC","",k.getTitle(),fingerprint,index(id,k.getTitle(),text));
        }
        store.pruneKnowledge(known.keySet(),ids); syncedAt=System.currentTimeMillis();
    }
    private String bounded(String text,int size) { return text.length()<=size?text:text.substring(0,size); }
    public record SearchResult(List<Map<String,Object>> citations,String retrievalMode) {}
    private record Retrieval(List<HybridRetriever.Hit> hits,double[] vector,Map<String,String> sourceUrls,String mode) {}

    /** Read-only evidence retrieval for the agent. Identity and session come from the server context. */
    public SearchResult search(String query,String sessionId) {
        String username=BaseContext.getCurrentUsername();
        String owner=username==null || username.isBlank()?"GUEST":"USER:"+username;
        return searchForOwner(query,owner,sessionId);
    }
    SearchResult searchForOwner(String query,String owner,String sessionId) {
        if(query==null || query.isBlank() || query.length()>500) throw new IllegalArgumentException("检索词需为 1～500 字符");
        if(owner==null || !(owner.equals("GUEST") || owner.matches("USER:[^\\p{Cntrl}]{1,120}")))
            throw new IllegalArgumentException("检索身份无效");
        Retrieval result=retrieve(query,owner,session(sessionId));
        List<Map<String,Object>> citations=new ArrayList<>();
        for(int i=0;i<result.hits().size();i++) {
            HybridRetriever.Hit hit=result.hits().get(i);
            RagStore.Chunk c=hit.chunk();
            Map<String,Object> citation=new LinkedHashMap<>();
            citation.put("id","S"+(i+1)); citation.put("documentId",c.documentId());
            citation.put("title",c.title()); citation.put("chunk",c.ordinal());
            citation.put("excerpt",c.text()); citation.put("score",hit.score());
            String url=result.sourceUrls().get(c.documentId());
            if(url!=null) citation.put("url",url);
            citations.add(citation);
        }
        return new SearchResult(citations,result.mode());
    }

    private Retrieval retrieve(String query,String owner,String session) {
        boolean indexed=false;
        if(model.embeddingsEnabled()) try {syncKnowledge();indexed=true;}
        catch(RuntimeException ex) {org.slf4j.LoggerFactory.getLogger(RagService.class).warn("rag_index_refresh_failed type={}",ex.getClass().getSimpleName());}
        double[] queryVector=new double[0];
        if(model.embeddingsEnabled()) try {queryVector=model.embed(query);} catch(IllegalStateException ignored) { /* Continue with lexical retrieval. */ }
        List<RagStore.Chunk> candidates;
        boolean vectorSearch=false;
        if(milvus!=null && milvus.enabled() && queryVector.length>0 && !store.hasPendingVectorJobs()) {
            try {
                candidates=store.chunksByIds(owner,session,milvus.search(queryVector,owner,session,model.embeddingVersion(),40));
                vectorSearch=true;
            } catch(RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(RagService.class).warn("milvus_search_failed type={}",ex.getClass().getSimpleName());
                candidates=new ArrayList<>(store.chunks(owner,session));
            }
        } else candidates=new ArrayList<>(store.chunks(owner,session));
        if(!indexed && !vectorSearch) {
            candidates.removeIf(c->c.documentId().startsWith("K"));
            for(Knowledge k:knowledge.listAll()) {
                int ordinal=0;
                for(String text:TextChunks.split(knowledgeText(k)))
                    candidates.add(new RagStore.Chunk("K"+k.getId()+"-"+ordinal,"K"+k.getId(),k.getTitle(),++ordinal,text,"[]","lexical"));
            }
        }
        List<HybridRetriever.Hit> hits=retriever.retrieve(query,queryVector,candidates,model.embeddingVersion(),6);
        Set<String> citedDocuments=new LinkedHashSet<>();
        for(HybridRetriever.Hit hit:hits)
            if(hit.chunk().documentId().startsWith("K")) citedDocuments.add(hit.chunk().documentId());
        Map<String,String> sourceUrls=Map.of();
        if(!citedDocuments.isEmpty()) try {
            Map<String,String> found=store.sourceUrls(citedDocuments);
            if(found!=null) sourceUrls=found;
        } catch(RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(RagService.class).warn("rag_source_lookup_failed type={}",ex.getClass().getSimpleName());
        }
        return new Retrieval(hits,queryVector,sourceUrls,vectorSearch?"milvus":queryVector.length>0?"hybrid":"lexical");
    }

    public Map<String,Object> ask(String question, String sessionId, boolean useMemory) {
        boolean authenticated=BaseContext.getCurrentUsername()!=null && !BaseContext.getCurrentUsername().isBlank();
        String owner=authenticated?owner():"GUEST",session=session(sessionId);
        useMemory=useMemory && authenticated;
        if (question==null || question.isBlank() || question.length()>2000) throw new IllegalArgumentException("问题需为 1～2000 字符");
        List<RagStore.Turn> recent=useMemory?store.recent(owner,session):List.of();
        String retrievalQuery=question;
        // Resolve short follow-up questions using the most recent user turn.
        if (!recent.isEmpty() && question.length()<30) retrievalQuery=recent.get(recent.size()-1).question()+"\n"+question;
        Retrieval retrieval=retrieve(retrievalQuery,owner,session);
        double[] queryVector=retrieval.vector();
        List<HybridRetriever.Hit> hits=retrieval.hits();
        Map<String,String> sourceUrls=retrieval.sourceUrls();
        StringBuilder context=new StringBuilder("<检索资料>\n");
        List<Map<String,Object>> citations=new ArrayList<>();
        for (int i=0;i<hits.size();i++) {
            RagStore.Chunk c=hits.get(i).chunk(); String label="S"+(i+1);
            String sourceUrl=sourceUrls.get(c.documentId());
            context.append('[').append(label).append("] ").append(c.title()).append(" / 片段 ").append(c.ordinal()).append('\n');
            if (sourceUrl!=null) context.append("原文链接：").append(sourceUrl).append('\n');
            context.append(c.text()).append('\n');
            Map<String,Object> citation=new LinkedHashMap<>();
            citation.put("id",label); citation.put("documentId",c.documentId()); citation.put("title",c.title());
            citation.put("chunk",c.ordinal()); citation.put("excerpt",c.text()); citation.put("score",hits.get(i).score());
            if (sourceUrl!=null) citation.put("url",sourceUrl);
            citations.add(citation);
        }
        context.append("</检索资料>\n");
        if (useMemory) {
            context.append("<历史记忆，仅用于理解用户，不是事实依据>\n").append(store.summary(owner,session));
            for (HybridRetriever.Hit hit : retriever.retrieve(question,queryVector,store.memories(owner),model.embeddingVersion(),3)) context.append('\n').append(bounded(hit.chunk().text(),1500));
            context.append("\n</历史记忆>\n<最近对话>\n");
            for (RagStore.Turn turn:recent) context.append("用户：").append(bounded(turn.question(),1000)).append("\n助手：").append(bounded(turn.answer(),1500)).append('\n');
            context.append("</最近对话>\n");
        }
        String answer; boolean generated=true;
        try { answer=model.answer(question,context.toString()); }
        catch (IllegalStateException ex) {
            generated=false;
            answer=hits.isEmpty()?"回答服务暂时不可用，且未检索到相关资料，请稍后重试。":"回答服务暂时不可用，以下为检索到的原文片段：\n";
            for (int i=0;i<hits.size();i++) answer+="\n[S"+(i+1)+"] "+hits.get(i).chunk().text()+"\n";
        }
        boolean saved=false;
        if (useMemory && generated) {
            try {
                double[] memoryVector=new double[0];
                if(queryVector.length>0) try { memoryVector=model.embed(question+"\n"+answer); } catch(IllegalStateException ignored) { /* Persist lexical memory. */ }
                store.remember(owner,session,question,answer,encode(memoryVector),model.embeddingVersion()); saved=true;
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(RagService.class).warn("RAG answer completed but memory persistence failed",ex);
            }
        }
        return Map.of("source",generated?"ai_knowledge":"fallback","answer",answer,"citations",citations,
                "retrievalMode",retrieval.mode(),"memoryEnabled",useMemory,"memorySaved",saved);
    }
    public List<Map<String,Object>> documents(String id) { return store.documents(owner(),session(id)); }
    public boolean delete(String id,String documentId) { return store.deleteDocument(owner(),session(id),documentId); }
    public void forget() { store.forget(owner()); }
    public void clear(String id) { store.clearSession(owner(),session(id)); }
}
