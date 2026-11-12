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
    }
    public List<Map<String,Object>> documents(String owner, String session) {
        return db.queryForList("SELECT id,title,created_at FROM rag_document WHERE owner=? AND session_id=? AND created_at>=? ORDER BY created_at", owner,session,retentionCutoff());
    }
    public boolean unchanged(String id, String fingerprint) {
        return !db.queryForList("SELECT id FROM rag_document WHERE id=? AND fingerprint=? AND (owner='PUBLIC' OR created_at>=?)",id,fingerprint,retentionCutoff()).isEmpty();
    }
    public void replace(String id, String owner, String session, String title, String fingerprint, List<Chunk> chunks) {
        tx.executeWithoutResult(status -> {
            db.update("DELETE FROM rag_document WHERE id=?",id);
            db.update("INSERT INTO rag_document(id,owner,session_id,title,fingerprint) VALUES(?,?,?,?,?)",id,owner,session,title,fingerprint);
            db.batchUpdate("INSERT INTO rag_chunk(id,document_id,ordinal,content,embedding,model) VALUES(?,?,?,?,?,?)",
                    chunks.stream().map(c -> new Object[]{c.id(),id,c.ordinal(),c.text(),c.vector(),c.model()}).toList());
        });
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
        if (!stale.isEmpty()) db.batchUpdate("DELETE FROM rag_document WHERE id=? AND owner='PUBLIC'",stale);
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
        return db.update("DELETE FROM rag_document WHERE id=? AND owner=? AND session_id=?",id,owner,session)>0;
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
        db.update("DELETE FROM rag_document WHERE owner<>'PUBLIC' AND created_at < ?",cutoff);
    }
}
