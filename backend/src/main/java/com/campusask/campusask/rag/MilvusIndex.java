package com.campusask.campusask.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Milvus is a rebuildable search index. MySQL remains authoritative for ownership and citations. */
@Component
public class MilvusIndex {
    private final RagStore store;
    private final RagModelClient modelClient;
    private final ObjectMapper json=new ObjectMapper();
    @Value("${rag.milvus.url:}") private String url;
    @Value("${rag.milvus.token:}") private String token;
    @Value("${rag.milvus.collection:campusask_chunks_v1}") private String collection;
    private volatile boolean collectionReady;
    public MilvusIndex(RagStore store,RagModelClient modelClient) { this.store=store;this.modelClient=modelClient; }
    public boolean enabled() { return url!=null && !url.isBlank() && modelClient.embeddingsEnabled(); }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    static String scope(String owner,String session) {
        return "PUBLIC".equals(owner)?"PUBLIC":digest(owner+"\u0000"+session);
    }
    private URI endpoint(String path) {
        URI base=URI.create(url);
        if(base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null
                || !("https".equalsIgnoreCase(base.getScheme()) ||
                    ("http".equalsIgnoreCase(base.getScheme()) &&
                     Set.of("localhost","127.0.0.1","::1").contains(base.getHost()))))
            throw new IllegalArgumentException("Milvus 地址必须为 HTTPS 或本机 HTTP");
        if(!collection.matches("[A-Za-z][A-Za-z0-9_]{0,254}"))
            throw new IllegalArgumentException("Milvus collection 名称无效");
        return URI.create(base.toString().replaceAll("/+$","")+path);
    }
    private JsonNode post(String path,Object body) {
        try {
            Map<String,String> headers=token==null || token.isBlank()?Map.of():Map.of("Authorization","Bearer "+token);
            JsonHttp.Response response=JsonHttp.post(endpoint(path),json.writeValueAsBytes(body),headers,20_000);
            if(response.status()!=200) throw new IllegalStateException("Milvus HTTP "+response.status());
            JsonNode result=json.readTree(response.body());
            if(result.path("code").asInt(-1)!=0) throw new IllegalStateException("Milvus 返回错误代码 "+result.path("code").asInt());
            return result;
        } catch(Exception ex) { throw new IllegalStateException("Milvus 请求失败",ex); }
    }
    private synchronized void ensureCollection(int dimension) {
        if(collectionReady) return;
        if(!post("/v2/vectordb/collections/has",Map.of("collectionName",collection)).path("data").path("has").asBoolean()) {
            List<Map<String,Object>> fields=new ArrayList<>();
            fields.add(Map.of("fieldName","id","dataType","VarChar","isPrimary",true,"elementTypeParams",Map.of("max_length","64")));
            fields.add(Map.of("fieldName","vector","dataType","FloatVector","elementTypeParams",Map.of("dim",Integer.toString(dimension))));
            for(String name:List.of("scope","model","document_id"))
                fields.add(Map.of("fieldName",name,"dataType","VarChar","elementTypeParams",Map.of("max_length","64")));
            fields.add(Map.of("fieldName","title","dataType","VarChar","elementTypeParams",Map.of("max_length","1024")));
            fields.add(Map.of("fieldName","content","dataType","VarChar","elementTypeParams",Map.of("max_length","4096")));
            post("/v2/vectordb/collections/create",Map.of(
                    "collectionName",collection,"schema",Map.of("autoID",false,"enableDynamicField",false,"fields",fields),
                    "indexParams",List.of(Map.of("fieldName","vector","indexName","vector_idx","metricType","COSINE",
                            "params",Map.of("index_type","AUTOINDEX")))));
        }
        collectionReady=true;
    }
    public List<String> search(double[] vector,String owner,String session,String modelVersion,int limit) {
        if(!enabled() || vector.length==0) return List.of();
        JsonNode response=post("/v2/vectordb/entities/search",Map.of(
                "collectionName",collection,"data",List.of(vector),"annsField","vector","limit",limit,
                "filter","(scope == \"PUBLIC\" or scope == \""+scope(owner,session)+"\") and model == \""+digest(modelVersion)+"\"",
                "outputFields",List.of("id"),"searchParams",Map.of("metricType","COSINE"),
                "consistencyLevel","Strong"));
        List<String> ids=new ArrayList<>();
        for(JsonNode row:response.path("data")) {
            String id=row.path("id").asText("");
            if(id.matches("[A-Za-z0-9-]{1,64}")) ids.add(id);
        }
        return ids;
    }
    /** Replays durable jobs in batches. Jobs are acknowledged only after Milvus confirms the request. */
    public int syncBatch() {
        if(!enabled()) return 0;
        store.bootstrapVectorJobs();
        List<RagStore.VectorJob> jobs=store.vectorJobs(100);
        if(jobs.isEmpty()) return 0;
        List<Map<String,Object>> upserts=new ArrayList<>();
        List<String> deletes=new ArrayList<>();
        int dimension=0;
        for(RagStore.VectorJob job:jobs) {
            RagStore.Chunk chunk="UPSERT".equals(job.operation())?store.vectorChunk(job.chunkId()):null;
            RagStore.VectorScope owner=chunk==null?null:store.vectorScope(chunk.documentId());
            if(chunk==null || owner==null || !chunk.model().equals(modelClient.embeddingVersion())) {
                deletes.add(job.chunkId()); continue;
            }
            try {
                double[] vector=json.readValue(chunk.vector(),double[].class);
                if(vector.length<2 || Arrays.stream(vector).anyMatch(v->!Double.isFinite(v))) {
                    deletes.add(job.chunkId()); continue;
                }
                if(dimension==0) dimension=vector.length;
                if(vector.length!=dimension) throw new IllegalStateException("Milvus 向量维度不一致，请更换 collection 并重建索引");
                upserts.add(Map.of("id",chunk.id(),"vector",vector,"scope",scope(owner.owner(),owner.session()),
                        "model",digest(chunk.model()),"document_id",chunk.documentId(),
                        "title",chunk.title(),"content",chunk.text()));
            } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { deletes.add(job.chunkId()); }
        }
        if(!upserts.isEmpty()) {
            ensureCollection(dimension);
            post("/v2/vectordb/entities/upsert",Map.of("collectionName",collection,"data",upserts));
        }
        if(!deletes.isEmpty()) {
            if(!collectionReady) {
                boolean exists=post("/v2/vectordb/collections/has",Map.of("collectionName",collection)).path("data").path("has").asBoolean();
                if(!exists) { jobs.forEach(store::finishVectorJob); return jobs.size(); }
                collectionReady=true;
            }
            String ids=deletes.stream().map(id->"\""+id+"\"").collect(java.util.stream.Collectors.joining(","));
            post("/v2/vectordb/entities/delete",Map.of("collectionName",collection,"filter","id in ["+ids+"]"));
        }
        jobs.forEach(store::finishVectorJob);
        return jobs.size();
    }
}
