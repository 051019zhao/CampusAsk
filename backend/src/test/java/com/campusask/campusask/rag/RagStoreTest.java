package com.campusask.campusask.rag;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL/transaction tests in MySQL compatibility mode; no production connection. */
class RagStoreTest {
    private JdbcTemplate db;
    private DriverManagerDataSource source;
    private RagStore store;
    @BeforeEach void setup() {
        source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(source);
        store=new RagStore(db,new DataSourceTransactionManager(source)); store.initialize();
    }
    private void document(String id,String owner,String session) {
        store.replace(id,owner,session,id,"hash",List.of(new RagStore.Chunk("C"+id,id,id,1,"校园资料","[1,0]","v1")));
    }
    @Test void persistsAcrossServiceRecreation() {
        document("D1","USER:alice","s1");
        store.remember("USER:alice","s1","我住五山","好的","[1,0]","v1");
        RagStore restarted=new RagStore(db,new DataSourceTransactionManager(source)); restarted.initialize();
        assertEquals(1,restarted.documents("USER:alice","s1").size());
        assertEquals("我住五山",restarted.recent("USER:alice","s1").get(0).question());
        assertTrue(restarted.summary("USER:alice","s1").contains("五山"));
    }
    @Test void publicFingerprintSnapshotPrunesOnlyRemovedKnowledge() {
        document("K1","PUBLIC",""); document("K2","PUBLIC","");
        document("D1","USER:alice","s1");
        Map<String,String> snapshot=store.publicFingerprints();
        assertEquals(Map.of("K1","hash","K2","hash"),snapshot);
        store.pruneKnowledge(snapshot.keySet(),Set.of("K1"));
        assertEquals(Set.of("K1","D1"),new HashSet<>(
                store.chunks("USER:alice","s1").stream().map(RagStore.Chunk::documentId).toList()));
    }
    @Test void citedKnowledgeResolvesOnlySafeCrawlerLinks() {
        db.execute("CREATE TABLE crawl_candidate(id VARCHAR(64) PRIMARY KEY,knowledge_id BIGINT,url VARCHAR(1024),reviewed_at TIMESTAMP)");
        db.update("INSERT INTO crawl_candidate(id,knowledge_id,url,reviewed_at) VALUES('a',1,'https://campus.example.edu/news/1.html',CURRENT_TIMESTAMP)");
        db.update("INSERT INTO crawl_candidate(id,knowledge_id,url,reviewed_at) VALUES('b',2,'http://campus.example.edu/news/2.html',CURRENT_TIMESTAMP)");
        assertEquals(Map.of("K1","https://campus.example.edu/news/1.html"),store.sourceUrls(Set.of("K1","K2","D1","Kbad")));
        assertTrue(store.sourceUrls(Set.of("D1")).isEmpty());
    }
    @Test void retrievalOnlySeesPublicAndOwnSessionDocuments() {
        document("K1","PUBLIC",""); document("D1","USER:alice","s1");
        document("D2","USER:bob","s1"); document("D3","USER:alice","s2");
        assertEquals(Set.of("K1","D1"),new HashSet<>(store.chunks("USER:alice","s1").stream().map(RagStore.Chunk::documentId).toList()));
        assertFalse(store.deleteDocument("USER:alice","s1","D2"));
        assertEquals(1,store.documents("USER:bob","s1").size());
    }
    @Test void documentDeletionCascadesAndReplaceIsAtomic() {
        document("D1","USER:alice","s1");
        assertThrows(RuntimeException.class,()->store.replace("D1","USER:alice","s1","bad","new",List.of(
                new RagStore.Chunk("duplicate","D1","bad",1,"a","[]","v1"),new RagStore.Chunk("duplicate","D1","bad",2,"b","[]","v1"))));
        assertTrue(store.unchanged("D1","hash"));
        assertEquals(1,store.chunks("USER:alice","s1").size());
        assertTrue(store.deleteDocument("USER:alice","s1","D1"));
        assertTrue(store.chunks("USER:alice","s1").isEmpty());
    }
    @Test void vectorJobsReplayExistingChunksAndTrackDeletion() {
        document("D1","USER:alice","s1");
        store.bootstrapVectorJobs();
        assertEquals("UPSERT",store.vectorJobs(10).get(0).operation());
        RagStore.VectorJob upsert=store.vectorJobs(10).get(0);
        assertEquals("CD1",store.vectorChunk(upsert.chunkId()).id());
        assertEquals(new RagStore.VectorScope("USER:alice","s1"),store.vectorScope("D1"));
        store.finishVectorJob(upsert);
        assertTrue(store.vectorJobs(10).isEmpty());
        assertTrue(store.deleteDocument("USER:alice","s1","D1"));
        assertEquals("DELETE",store.vectorJobs(10).get(0).operation());
        assertNull(store.vectorChunk("CD1"));
    }
    @Test void vectorResultsAreRecheckedAgainstMysqlPermissions() {
        document("K1","PUBLIC",""); document("D1","USER:alice","s1");
        document("D2","USER:bob","s1"); document("D3","USER:alice","s2");
        assertEquals(Set.of("CK1","CD1"),new HashSet<>(store.chunksByIds("USER:alice","s1",List.of("CK1","CD1","CD2","CD3"))
                .stream().map(RagStore.Chunk::id).toList()));
    }
    @Test void forgetDeletesOnlyCurrentUserMemory() {
        store.remember("USER:alice","s1","a","b","[1,0]","v1");
        store.remember("USER:bob","s1","c","d","[1,0]","v1");
        store.forget("USER:alice");
        assertTrue(store.memories("USER:alice").isEmpty());
        assertEquals("",store.summary("USER:alice","s1"));
        assertEquals(1,store.memories("USER:bob").size());
    }
    @Test void recentWindowIsBoundedAndChronological() {
        for(int i=0;i<8;i++) store.remember("USER:alice","s1","q"+i,"a","[1,0]","v1");
        List<RagStore.Turn> recent=store.recent("USER:alice","s1");
        assertEquals(6,recent.size()); assertEquals("q2",recent.get(0).question()); assertEquals("q7",recent.get(5).question());
    }
    @Test void clearSessionPreservesOtherSessionAndOtherUser() {
        document("D1","USER:alice","s1"); document("D2","USER:alice","s2");
        store.remember("USER:alice","s1","q","a","[1,0]","v1");
        store.remember("USER:alice","s2","q","a","[1,0]","v1");
        store.clearSession("USER:alice","s1");
        assertTrue(store.documents("USER:alice","s1").isEmpty());
        assertTrue(store.recent("USER:alice","s1").isEmpty());
        assertEquals(1,store.documents("USER:alice","s2").size());
        assertEquals(1,store.recent("USER:alice","s2").size());
    }
    @Test void retentionDeletesExpiredEpisodesAttachmentsAndSummaryFacts() {
        document("D1","USER:alice","s1"); document("K1","PUBLIC","");
        store.remember("USER:alice","s1","旧资料","旧回答","[1,0]","v1");
        java.sql.Timestamp old=java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(91)));
        db.update("UPDATE rag_turn SET created_at=?",old);
        db.update("UPDATE rag_document SET created_at=?",old);
        store.expire();
        assertTrue(store.memories("USER:alice").isEmpty());
        assertTrue(store.documents("USER:alice","s1").isEmpty());
        assertEquals("",store.summary("USER:alice","s1"));
        assertEquals(1,store.chunks("USER:alice","s1").size());
    }
    @Test void expiryKeepsSummariesOfUnaffectedSessions() {
        store.remember("USER:alice","old","过期问题","旧回答","[1,0]","v1");
        store.remember("USER:bob","current","当前问题","当前回答","[1,0]","v1");
        java.sql.Timestamp old=java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(91)));
        db.update("UPDATE rag_turn SET created_at=? WHERE owner=?",old,"USER:alice");
        store.expire();
        assertEquals("",store.summary("USER:alice","old"));
        assertTrue(store.summary("USER:bob","current").contains("当前问题"));
    }
    @Test void expiredDataCannotBeReadBeforeMaintenanceRuns() {
        document("D1","USER:alice","s1");store.remember("USER:alice","s1","过期用户背景","旧回答","[1,0]","v1");
        java.sql.Timestamp old=java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(91)));
        db.update("UPDATE rag_turn SET created_at=?",old);db.update("UPDATE rag_document SET created_at=?",old);
        assertTrue(store.documents("USER:alice","s1").isEmpty());assertTrue(store.chunks("USER:alice","s1").isEmpty());
        assertTrue(store.recent("USER:alice","s1").isEmpty());assertTrue(store.memories("USER:alice").isEmpty());
        assertEquals("",store.summary("USER:alice","s1"));assertFalse(store.unchanged("D1","hash"));
    }
}
