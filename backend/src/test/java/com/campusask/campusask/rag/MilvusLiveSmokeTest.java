package com.campusask.campusask.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in local test against a real Milvus; uses isolated H2 and removes its own collection. */
class MilvusLiveSmokeTest {
    @Test
    @EnabledIfEnvironmentVariable(named="MILVUS_LIVE_URL",matches=".+")
    void syncSearchAndDeleteAgainstRealMilvus() throws Exception {
        String url=System.getenv("MILVUS_LIVE_URL"),token=Objects.toString(System.getenv("MILVUS_LIVE_TOKEN"),"");
        String collection="campusask_smoke_"+UUID.randomUUID().toString().replace("-","");
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        RagStore store=new RagStore(new JdbcTemplate(source),new DataSourceTransactionManager(source));
        store.initialize();
        RagModelClient model=mock(RagModelClient.class);
        when(model.embeddingsEnabled()).thenReturn(true);
        when(model.embeddingVersion()).thenReturn("local-smoke-model");
        MilvusIndex index=new MilvusIndex(store,model);
        ReflectionTestUtils.setField(index,"url",url);
        ReflectionTestUtils.setField(index,"token",token);
        ReflectionTestUtils.setField(index,"collection",collection);
        String chunkId=UUID.randomUUID().toString();
        try {
            store.replace("K999999","PUBLIC","","测试公告","hash",List.of(new RagStore.Chunk(
                    chunkId,"K999999","测试公告",1,"图书馆开放","[1,0]","local-smoke-model")));
            assertEquals(1,index.syncBatch());
            assertTrue(index.search(new double[]{1,0},"GUEST","session","local-smoke-model",5).contains(chunkId));
            assertTrue(store.deleteDocument("PUBLIC","","K999999"));
            assertEquals(1,index.syncBatch());
            assertFalse(index.search(new double[]{1,0},"GUEST","session","local-smoke-model",5).contains(chunkId));
        } finally {
            Map<String,String> headers=token.isBlank()?Map.of():Map.of("Authorization","Bearer "+token);
            JsonHttp.post(URI.create(url+"/v2/vectordb/collections/drop"),
                    ("{\"collectionName\":\""+collection+"\"}").getBytes(StandardCharsets.UTF_8),headers,20_000);
        }
    }
}
