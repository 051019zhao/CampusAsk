package com.campusask.campusask.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MilvusIndexTest {
    @Test void syncsBatchAndSearchesOnlyAllowedScopeAndModel() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> upsertBody=new AtomicReference<>(),searchBody=new AtomicReference<>();
        for(String path:List.of("collections/has","collections/create","entities/upsert","entities/search")) {
            server.createContext("/v2/vectordb/"+path,exchange -> {
                String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                if(path.equals("entities/upsert")) upsertBody.set(body);
                if(path.equals("entities/search")) searchBody.set(body);
                String data=path.equals("collections/has")?"{\"has\":false}":
                        path.equals("entities/search")?"[{\"id\":\"chunk-1\"}]":"{}";
                byte[] response=("{\"code\":0,\"data\":"+data+"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(200,response.length);
                try(var output=exchange.getResponseBody()) { output.write(response); }
            });
        }
        server.start();
        try {
            RagStore store=mock(RagStore.class);
            RagModelClient model=mock(RagModelClient.class);
            when(model.embeddingsEnabled()).thenReturn(true);
            when(model.embeddingVersion()).thenReturn("v1");
            RagStore.VectorJob job=new RagStore.VectorJob("chunk-1","UPSERT",1);
            when(store.vectorJobs(100)).thenReturn(List.of(job));
            when(store.vectorChunk("chunk-1")).thenReturn(new RagStore.Chunk(
                    "chunk-1","K1","校园公告",1,"图书馆开放","[1,0]","v1"));
            when(store.vectorScope("K1")).thenReturn(new RagStore.VectorScope("PUBLIC",""));
            MilvusIndex index=new MilvusIndex(store,model);
            ReflectionTestUtils.setField(index,"url","http://127.0.0.1:"+server.getAddress().getPort());
            ReflectionTestUtils.setField(index,"collection","campusask_test");
            assertEquals(1,index.syncBatch());
            verify(store).finishVectorJob(job);
            ObjectMapper json=new ObjectMapper();
            JsonNode upsert=json.readTree(upsertBody.get());
            assertEquals("图书馆开放",upsert.path("data").path(0).path("content").asText());
            assertEquals("PUBLIC",upsert.path("data").path(0).path("scope").asText());
            assertEquals(List.of("chunk-1"),index.search(new double[]{1,0},"USER:alice","s1","v1",40));
            String filter=json.readTree(searchBody.get()).path("filter").asText();
            assertTrue(filter.contains(MilvusIndex.digest("USER:alice\u0000s1")));
            assertTrue(filter.contains(MilvusIndex.digest("v1")));
        } finally { server.stop(0); }
    }
}
