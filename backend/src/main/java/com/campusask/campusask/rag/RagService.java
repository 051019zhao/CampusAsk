package com.campusask.campusask.rag;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.mapper.KnowledgeMapper;
import com.campusask.campusask.entity.Knowledge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
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
    public Map<String,Object> ask(String question, String sessionId, boolean useMemory) {
        boolean authenticated=BaseContext.getCurrentUsername()!=null && !BaseContext.getCurrentUsername().isBlank();
        String owner=authenticated?owner():"GUEST",session=session(sessionId);
        useMemory=useMemory && authenticated;
        if (question==null || question.isBlank() || question.length()>2000) throw new IllegalArgumentException("问题需为 1～2000 字符");
        boolean indexed=false;
        if(model.embeddingsEnabled()) try {syncKnowledge();indexed=true;}
        catch(RuntimeException ex) {org.slf4j.LoggerFactory.getLogger(RagService.class).warn("rag_index_refresh_failed type={}",ex.getClass().getSimpleName());}
        List<RagStore.Turn> recent=useMemory?store.recent(owner,session):List.of();
        String retrievalQuery=question;
        // Resolve short follow-up questions using the most recent user turn.
        if (!recent.isEmpty() && question.length()<30) retrievalQuery=recent.get(recent.size()-1).question()+"\n"+question;
        double[] queryVector=new double[0];
        if(model.embeddingsEnabled()) try {queryVector=model.embed(retrievalQuery);} catch(IllegalStateException ignored) { /* Continue with lexical retrieval. */ }
        List<RagStore.Chunk> candidates=new ArrayList<>(store.chunks(owner,session));
        if(!indexed) {
            candidates.removeIf(c->c.documentId().startsWith("K"));
            for(Knowledge k:knowledge.listAll()) {
                int ordinal=0;
                for(String text:TextChunks.split(knowledgeText(k)))
                    candidates.add(new RagStore.Chunk("K"+k.getId()+"-"+ordinal,"K"+k.getId(),k.getTitle(),++ordinal,text,"[]","lexical"));
            }
        }
        List<HybridRetriever.Hit> hits=retriever.retrieve(retrievalQuery,queryVector,candidates,model.embeddingVersion(),6);
        StringBuilder context=new StringBuilder("<检索资料>\n");
        List<Map<String,Object>> citations=new ArrayList<>();
        for (int i=0;i<hits.size();i++) {
            RagStore.Chunk c=hits.get(i).chunk(); String label="S"+(i+1);
            context.append('[').append(label).append("] ").append(c.title()).append(" / 片段 ").append(c.ordinal()).append('\n').append(c.text()).append('\n');
            citations.add(Map.of("id",label,"documentId",c.documentId(),"title",c.title(),"chunk",c.ordinal(),"excerpt",c.text(),"score",hits.get(i).score()));
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
                "retrievalMode",queryVector.length>0?"hybrid":"lexical","memoryEnabled",useMemory,"memorySaved",saved);
    }
    public List<Map<String,Object>> documents(String id) { return store.documents(owner(),session(id)); }
    public boolean delete(String id,String documentId) { return store.deleteDocument(owner(),session(id),documentId); }
    public void forget() { store.forget(owner()); }
    public void clear(String id) { store.clearSession(owner(),session(id)); }
}
