package com.campusask.campusask.rag;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

@Repository
public class RagStore {
    public record Chunk(String id, String documentId, String title, int ordinal, String text, String vector, String model) {}
    public record Turn(String question, String answer) {}
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    public RagStore(JdbcTemplate db, PlatformTransactionManager manager) {
        this.db = db; this.tx = new TransactionTemplate(manager);
    }
    @PostConstruct
    void initialize() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS rag_document (
              id VARCHAR(64) PRIMARY KEY, owner VARCHAR(128) NOT NULL, session_id VARCHAR(100) NOT NULL,
              title VARCHAR(255) NOT NULL, fingerprint VARCHAR(64) NOT NULL,
              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              INDEX rag_owner_session(owner,session_id), INDEX rag_document_expiry(created_at))
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS rag_chunk (
              id VARCHAR(64) PRIMARY KEY, document_id VARCHAR(64) NOT NULL, ordinal INT NOT NULL,
              content TEXT NOT NULL, embedding MEDIUMTEXT NOT NULL, model VARCHAR(512) NOT NULL,
              INDEX rag_document_chunks(document_id),
              FOREIGN KEY (document_id) REFERENCES rag_document(id) ON DELETE CASCADE)
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS rag_turn (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, owner VARCHAR(128) NOT NULL,
              session_id VARCHAR(100) NOT NULL, question TEXT NOT NULL, answer TEXT NOT NULL,
              embedding MEDIUMTEXT NOT NULL, model VARCHAR(512) NOT NULL,
              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              INDEX rag_turn_owner(owner,session_id,id), INDEX rag_turn_expiry(created_at))
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS rag_summary (
              owner VARCHAR(128) NOT NULL, session_id VARCHAR(100) NOT NULL,
              content TEXT NOT NULL, PRIMARY KEY(owner,session_id))
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS rag_vector_job (
              chunk_id VARCHAR(64) PRIMARY KEY, operation VARCHAR(8) NOT NULL,
              version BIGINT NOT NULL DEFAULT 1, queued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              INDEX rag_vector_job_queue(queued_at))
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS rag_vector_state (
              name VARCHAR(64) PRIMARY KEY, state_value VARCHAR(255) NOT NULL)
            """);
    }
    public List<Map<String,Object>> documents(String owner, String session) {
        return db.queryForList("SELECT id,title,created_at FROM rag_document WHERE owner=? AND session_id=? AND created_at>=? ORDER BY created_at", owner,session,retentionCutoff());
    }
    public boolean unchanged(String id, String fingerprint) {
        return !db.queryForList("SELECT id FROM rag_document WHERE id=? AND fingerprint=? AND (owner='PUBLIC' OR created_at>=?)",id,fingerprint,retentionCutoff()).isEmpty();
    }
    public void replace(String id, String owner, String session, String title, String fingerprint, List<Chunk> chunks) {
        tx.executeWithoutResult(status -> {
            queueDocumentDeletion(id);
            db.update("DELETE FROM rag_document WHERE id=?",id);
            db.update("INSERT INTO rag_document(id,owner,session_id,title,fingerprint) VALUES(?,?,?,?,?)",id,owner,session,title,fingerprint);
            db.batchUpdate("INSERT INTO rag_chunk(id,document_id,ordinal,content,embedding,model) VALUES(?,?,?,?,?,?)",
                    chunks.stream().map(c -> new Object[]{c.id(),id,c.ordinal(),c.text(),c.vector(),c.model()}).toList());
            for (Chunk chunk:chunks) queueVector(chunk.id(),"UPSERT");
        });
    }
    private void queueVector(String chunkId,String operation) {
        db.update("""
            INSERT INTO rag_vector_job(chunk_id,operation) VALUES(?,?)
            ON DUPLICATE KEY UPDATE operation=VALUES(operation),version=version+1,queued_at=CURRENT_TIMESTAMP
            """,chunkId,operation);
    }
    private void queueDocumentDeletion(String documentId) {
        for (String id:db.queryForList("SELECT id FROM rag_chunk WHERE document_id=?",String.class,documentId))
            queueVector(id,"DELETE");
    }
    public record VectorJob(String chunkId,String operation,long version) {}
    public List<VectorJob> vectorJobs(int limit) {
        return db.query("SELECT chunk_id,operation,version FROM rag_vector_job ORDER BY queued_at,chunk_id LIMIT ?",
                (r,n)->new VectorJob(r.getString(1),r.getString(2),r.getLong(3)),limit);
    }
    public boolean hasPendingVectorJobs() {
        return !db.queryForList("SELECT chunk_id FROM rag_vector_job LIMIT 1").isEmpty();
    }
    public void finishVectorJob(VectorJob job) {
        db.update("DELETE FROM rag_vector_job WHERE chunk_id=? AND version=?",job.chunkId(),job.version());
    }
    public Chunk vectorChunk(String id) {
        List<Chunk> found=db.query("""
            SELECT c.id,c.document_id,c.ordinal,c.content,c.embedding,c.model,d.title
            FROM rag_chunk c JOIN rag_document d ON d.id=c.document_id WHERE c.id=?
            """,(r,n)->new Chunk(r.getString(1),r.getString(2),r.getString(7),r.getInt(3),r.getString(4),r.getString(5),r.getString(6)),id);
        return found.isEmpty()?null:found.get(0);
    }
    public record VectorScope(String owner,String session) {}
    public VectorScope vectorScope(String documentId) {
        List<VectorScope> found=db.query("SELECT owner,session_id FROM rag_document WHERE id=?",
                (r,n)->new VectorScope(r.getString(1),r.getString(2)),documentId);
        return found.isEmpty()?null:found.get(0);
    }
    /** Idempotent, one-time migration of existing MySQL chunks into the durable job queue. */
    public void bootstrapVectorJobs() {
        tx.executeWithoutResult(status -> {
            if (!db.queryForList("SELECT name FROM rag_vector_state WHERE name='bootstrapped'").isEmpty()) return;
            db.update("""
                INSERT INTO rag_vector_job(chunk_id,operation)
                SELECT c.id,'UPSERT' FROM rag_chunk c
                WHERE NOT EXISTS (SELECT 1 FROM rag_vector_job j WHERE j.chunk_id=c.id)
                """);
            db.update("INSERT INTO rag_vector_state(name,state_value) VALUES('bootstrapped','1')");
        });
    }
    public List<Chunk> chunksByIds(String owner,String session,Collection<String> ids) {
        if(ids.isEmpty()) return List.of();
        List<String> valid=ids.stream().filter(id->id!=null && id.matches("[A-Za-z0-9-]{1,64}"))
                .distinct().limit(100).toList();
        if(valid.isEmpty()) return List.of();
        String placeholders=String.join(",",Collections.nCopies(valid.size(),"?"));
        List<Object> params=new ArrayList<>(valid); params.add(owner);params.add(session);params.add(retentionCutoff());
        return db.query("""
            SELECT c.id,c.document_id,c.ordinal,c.content,c.embedding,c.model,d.title
            FROM rag_chunk c JOIN rag_document d ON d.id=c.document_id
            WHERE c.id IN (%s) AND (d.owner='PUBLIC' OR (d.owner=? AND d.session_id=? AND d.created_at>=?))
            """.formatted(placeholders),(r,n)->new Chunk(r.getString(1),r.getString(2),r.getString(7),r.getInt(3),r.getString(4),r.getString(5),r.getString(6)),params.toArray());
    }
    public Map<String,String> publicFingerprints() {
        return db.query("SELECT id,fingerprint FROM rag_document WHERE owner='PUBLIC'", rows -> {
            Map<String,String> fingerprints=new HashMap<>();
            while (rows.next()) fingerprints.put(rows.getString(1),rows.getString(2));
            return fingerprints;
        });
    }
    /** Resolve links only for knowledge articles actually selected as answer citations. */
    public Map<String,String> sourceUrls(Set<String> documentIds) {
        List<Long> ids=new ArrayList<>();
        for (String id:documentIds) {
            if (id==null || !id.matches("K[1-9][0-9]*")) continue;
            try { ids.add(Long.parseLong(id.substring(1))); }
            catch (NumberFormatException ignored) { /* Ignore malformed document IDs. */ }
        }
        if (ids.isEmpty()) return Map.of();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        return db.query("SELECT knowledge_id,url FROM crawl_candidate WHERE knowledge_id IN ("+placeholders+") ORDER BY reviewed_at DESC",
                rows -> {
                    Map<String,String> urls=new HashMap<>();
                    while (rows.next()) {
                        String url=rows.getString("url");
                        if (url==null) continue;
                        try {
                            java.net.URI parsed=java.net.URI.create(url);
                            if ("https".equalsIgnoreCase(parsed.getScheme()) && parsed.getHost()!=null && parsed.getUserInfo()==null)
                                urls.putIfAbsent("K"+rows.getLong("knowledge_id"),url);
                        } catch (IllegalArgumentException ignored) { /* Never expose invalid links. */ }
                    }
                    return urls;
                },ids.toArray());
    }
    public void pruneKnowledge(Set<String> knownIds, Set<String> currentIds) {
        List<Object[]> stale=knownIds.stream().filter(id -> !currentIds.contains(id))
                .map(id -> new Object[]{id}).toList();
        if (!stale.isEmpty()) tx.executeWithoutResult(status -> {
            for(Object[] row:stale) queueDocumentDeletion(row[0].toString());
            db.batchUpdate("DELETE FROM rag_document WHERE id=? AND owner='PUBLIC'",stale);
        });
    }
    public List<Chunk> chunks(String owner, String session) {
        return db.query("""
            SELECT c.id,c.document_id,c.ordinal,c.content,c.embedding,c.model,d.title
            FROM rag_document d JOIN rag_chunk c ON c.document_id=d.id
            WHERE d.owner='PUBLIC'
            UNION ALL
            SELECT c.id,c.document_id,c.ordinal,c.content,c.embedding,c.model,d.title
            FROM rag_document d JOIN rag_chunk c ON c.document_id=d.id
            WHERE d.owner=? AND d.session_id=? AND d.created_at>=?
            """, (r,n) -> new Chunk(r.getString("id"),r.getString("document_id"),r.getString("title"),r.getInt("ordinal"),r.getString("content"),r.getString("embedding"),r.getString("model")),owner,session,retentionCutoff());
    }
    public boolean deleteDocument(String owner, String session, String id) {
        return tx.execute(status -> {
            if(db.queryForList("SELECT id FROM rag_document WHERE id=? AND owner=? AND session_id=?",id,owner,session).isEmpty()) return false;
            queueDocumentDeletion(id);
            return db.update("DELETE FROM rag_document WHERE id=? AND owner=? AND session_id=?",id,owner,session)>0;
        });
    }
    public List<Turn> recent(String owner, String session) {
        List<Turn> turns = db.query("SELECT question,answer FROM rag_turn WHERE owner=? AND session_id=? AND created_at>=? ORDER BY id DESC LIMIT 6",
                (r,n) -> new Turn(r.getString(1),r.getString(2)),owner,session,retentionCutoff());
        Collections.reverse(turns); return turns;
    }
    public List<Chunk> memories(String owner) {
        return db.query("SELECT * FROM rag_turn WHERE owner=? AND created_at>=? ORDER BY id DESC LIMIT 200",(r,n) -> new Chunk("M"+r.getLong("id"),r.getString("session_id"),"历史对话",0,r.getString("question")+"\n"+r.getString("answer"),r.getString("embedding"),r.getString("model")),owner,retentionCutoff());
    }
    public String summary(String owner, String session) {
        List<String> questions=db.queryForList("SELECT question FROM rag_turn WHERE owner=? AND session_id=? AND created_at>=? ORDER BY id DESC LIMIT 20",String.class,owner,session,retentionCutoff());
        Collections.reverse(questions);
        String text=String.join("\n用户：",questions);
        return text.length()>2400?text.substring(text.length()-2400):text;
    }
    private java.sql.Timestamp retentionCutoff() {return java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(90)));}
    public void remember(String owner, String session, String question, String answer, String vector, String model) {
        tx.executeWithoutResult(status -> {
            db.update("INSERT INTO rag_turn(owner,session_id,question,answer,embedding,model) VALUES(?,?,?,?,?,?)",owner,session,question,answer,vector,model);
            // Extractive rolling summary stores user statements only, so assistant guesses are not facts.
            List<String> questions = db.queryForList("SELECT question FROM rag_turn WHERE owner=? AND session_id=? ORDER BY id DESC LIMIT 20",String.class,owner,session);
            Collections.reverse(questions);
            String summary = String.join("\n用户：",questions);
            if (summary.length() > 2400) summary = summary.substring(summary.length()-2400);
            db.update("INSERT INTO rag_summary(owner,session_id,content) VALUES(?,?,?) ON DUPLICATE KEY UPDATE content=VALUES(content)",owner,session,summary);
            // Retain at most the newest 200 episodes per user; time-based retention is also enforced.
            List<Long> cutoff = db.queryForList("SELECT id FROM rag_turn WHERE owner=? ORDER BY id DESC LIMIT 1 OFFSET 199",Long.class,owner);
            if (!cutoff.isEmpty()) db.update("DELETE FROM rag_turn WHERE owner=? AND id<?",owner,cutoff.get(0));
        });
    }
    public void forget(String owner) {
        tx.executeWithoutResult(status -> {
            db.update("DELETE FROM rag_turn WHERE owner=?",owner);
            db.update("DELETE FROM rag_summary WHERE owner=?",owner);
        });
    }
    public void clearSession(String owner, String session) {
        tx.executeWithoutResult(status -> {
            for(String id:db.queryForList("SELECT id FROM rag_document WHERE owner=? AND session_id=?",String.class,owner,session))
                queueDocumentDeletion(id);
            db.update("DELETE FROM rag_turn WHERE owner=? AND session_id=?",owner,session);
            db.update("DELETE FROM rag_summary WHERE owner=? AND session_id=?",owner,session);
            db.update("DELETE FROM rag_document WHERE owner=? AND session_id=?",owner,session);
        });
    }
    public void expire() {
        java.sql.Timestamp cutoff = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(90)));
        // Only sessions with expired turns need their extractive summaries rebuilt.
        List<Map<String,Object>> affected=db.queryForList(
                "SELECT DISTINCT owner,session_id FROM rag_turn WHERE created_at < ?",cutoff);
        db.update("DELETE FROM rag_turn WHERE created_at < ?",cutoff);
        for (Map<String,Object> row: affected) {
            String owner=row.get("owner").toString(), session=row.get("session_id").toString();
            List<String> questions=db.queryForList("SELECT question FROM rag_turn WHERE owner=? AND session_id=? ORDER BY id DESC LIMIT 20",String.class,owner,session);
            Collections.reverse(questions);
            String summary=String.join("\n用户：",questions);
            if (summary.length()>2400) summary=summary.substring(summary.length()-2400);
            if (questions.isEmpty()) db.update("DELETE FROM rag_summary WHERE owner=? AND session_id=?",owner,session);
            else db.update("UPDATE rag_summary SET content=? WHERE owner=? AND session_id=?",summary,owner,session);
        }
        tx.executeWithoutResult(status -> {
            for(String id:db.queryForList("SELECT id FROM rag_document WHERE owner<>'PUBLIC' AND created_at < ?",String.class,cutoff))
                queueDocumentDeletion(id);
            db.update("DELETE FROM rag_document WHERE owner<>'PUBLIC' AND created_at < ?",cutoff);
        });
    }
}
