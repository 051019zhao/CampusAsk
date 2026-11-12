package com.campusask.campusask.crawler;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.sql.*;
import java.util.*;

@Repository
public class CrawlStore {
    // MySQL TIMESTAMP cannot represent Unix epoch itself after timezone conversion.
    private static final java.time.Instant RELEASED_AT = java.time.Instant.parse("2000-01-01T00:00:00Z");
    public record Candidate(String id,String url,String title,String content,String fingerprint,String status,Long knowledgeId,String fetchedAt) {}
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    public CrawlStore(JdbcTemplate db,PlatformTransactionManager manager) { this.db=db; this.tx=new TransactionTemplate(manager); }
    @PostConstruct
    void initialize() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS crawl_candidate (
              id VARCHAR(64) PRIMARY KEY, url VARCHAR(1024) NOT NULL, title VARCHAR(255) NOT NULL,
              content MEDIUMTEXT NOT NULL, fingerprint VARCHAR(64) NOT NULL,
              status VARCHAR(20) NOT NULL, knowledge_id BIGINT NULL,
              fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              reviewed_at TIMESTAMP NULL, reviewed_by BIGINT NULL,
              INDEX crawl_review(status,fetched_at))
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS crawl_run (
              id VARCHAR(36) PRIMARY KEY, status VARCHAR(20) NOT NULL,
              started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMP NULL,
              visited INT NOT NULL DEFAULT 0, changed_count INT NOT NULL DEFAULT 0,
              failed INT NOT NULL DEFAULT 0, detail VARCHAR(1000) NOT NULL DEFAULT '')
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS crawl_lease (
              id INT PRIMARY KEY, holder VARCHAR(36) NOT NULL, expires_at TIMESTAMP NOT NULL)
            """);
        db.update("INSERT INTO crawl_lease(id,holder,expires_at) VALUES(1,'',?) ON DUPLICATE KEY UPDATE id=id",Timestamp.from(RELEASED_AT));
    }
    public boolean acquire(String run) {
        boolean acquired=db.update("UPDATE crawl_lease SET holder=?,expires_at=? WHERE id=1 AND expires_at<CURRENT_TIMESTAMP",run,
                Timestamp.from(java.time.Instant.now().plusSeconds(900)))==1;
        if(acquired) db.update("UPDATE crawl_run SET status='ABANDONED',finished_at=CURRENT_TIMESTAMP,detail='Previous worker lease expired' WHERE status='RUNNING' AND started_at<?",Timestamp.from(java.time.Instant.now().minusSeconds(900)));
        return acquired;
    }
    public void release(String run) { db.update("UPDATE crawl_lease SET expires_at=? WHERE id=1 AND holder=?",Timestamp.from(RELEASED_AT),run); }
    public void start(String run) { db.update("INSERT INTO crawl_run(id,status) VALUES(?,'RUNNING')",run); }
    public void finish(String run,int visited,int changed,int failed,String detail) {
        db.update("UPDATE crawl_run SET status=?,finished_at=CURRENT_TIMESTAMP,visited=?,changed_count=?,failed=?,detail=? WHERE id=?",
                failed==0?"SUCCESS":visited==failed?"FAILED":"PARTIAL",visited,changed,failed,detail.substring(0,Math.min(detail.length(),1000)),run);
        db.update("DELETE FROM crawl_run WHERE started_at<?",Timestamp.from(java.time.Instant.now().minusSeconds(90L*86400)));
    }
    public List<Map<String,Object>> runs() { return db.queryForList("SELECT * FROM crawl_run ORDER BY started_at DESC LIMIT 20"); }
    public boolean stage(CrawlExtractor.Article article) {
        String id=UUID.nameUUIDFromBytes(article.url().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        List<String> previous=db.queryForList("SELECT fingerprint FROM crawl_candidate WHERE id=?",String.class,id);
        if(!previous.isEmpty()&&previous.get(0).equals(article.fingerprint())) {
            db.update("UPDATE crawl_candidate SET fetched_at=CURRENT_TIMESTAMP WHERE id=?",id); return false;
        }
        db.update("""
            INSERT INTO crawl_candidate(id,url,title,content,fingerprint,status) VALUES(?,?,?,?,?,'PENDING')
            ON DUPLICATE KEY UPDATE title=VALUES(title),content=VALUES(content),fingerprint=VALUES(fingerprint),
              status='PENDING',fetched_at=CURRENT_TIMESTAMP,reviewed_at=NULL,reviewed_by=NULL
            """,id,article.url(),article.title(),article.content(),article.fingerprint());
        return true;
    }
    private final RowMapper<Candidate> mapper=(r,n)->new Candidate(r.getString("id"),r.getString("url"),r.getString("title"),r.getString("content"),r.getString("fingerprint"),r.getString("status"),r.getObject("knowledge_id",Long.class),r.getString("fetched_at"));
    public Map<String,Object> candidates(String status,int page) {
        if(!Set.of("PENDING","APPROVED","REJECTED").contains(status)||page<1||page>10000) throw new IllegalArgumentException("筛选或页码无效");
        return Map.of("items",db.query("SELECT * FROM crawl_candidate WHERE status=? ORDER BY fetched_at DESC LIMIT 20 OFFSET ?",mapper,status,(page-1)*20),
                "total",Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM crawl_candidate WHERE status=?",Long.class,status)),"page",page);
    }
    public long approve(String id,String fingerprint,String title,String content,String category,Long reviewer) {
        return Objects.requireNonNull(tx.execute(status -> {
            Candidate c=locked(id,fingerprint);
            if(c.status().equals("APPROVED")) return c.knowledgeId();
            if(!c.status().equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT,"该版本已拒绝，不能直接入库");
            String published=content.trim()+"\n\n来源："+c.url()+"\n抓取时间："+c.fetchedAt();
            if(published.length()>5000) throw new IllegalArgumentException("正文及来源信息合计不能超过 5000 字符，请先整理正文");
            Long knowledgeId=c.knowledgeId();
            int updated=knowledgeId==null?0:db.update("UPDATE knowledge SET title=?,content=?,category=? WHERE id=?",title,published,category,knowledgeId);
            if(updated==0) {
                GeneratedKeyHolder keys=new GeneratedKeyHolder();
                db.update(connection -> {
                    PreparedStatement p=connection.prepareStatement("INSERT INTO knowledge(title,content,category,keywords,view_count,created_by,created_at) VALUES(?,?,?,'',0,?,CURRENT_TIMESTAMP)",Statement.RETURN_GENERATED_KEYS);
                    p.setString(1,title); p.setString(2,published); p.setString(3,category); p.setObject(4,reviewer); return p;
                },keys);
                knowledgeId=Objects.requireNonNull(keys.getKey()).longValue();
            }
            db.update("UPDATE crawl_candidate SET status='APPROVED',knowledge_id=?,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=? WHERE id=?",knowledgeId,reviewer,id);
            return knowledgeId;
        }));
    }
    public void reject(String id,String fingerprint,Long reviewer) {
        tx.executeWithoutResult(status -> {
            Candidate c=locked(id,fingerprint);
            if(!c.status().equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT,"该内容已审核");
            db.update("UPDATE crawl_candidate SET status='REJECTED',reviewed_at=CURRENT_TIMESTAMP,reviewed_by=? WHERE id=?",reviewer,id);
        });
    }
    private Candidate locked(String id,String fingerprint) {
        Candidate c=db.query("SELECT * FROM crawl_candidate WHERE id=? FOR UPDATE",mapper,id).stream().findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"抓取记录不存在"));
        if(!c.fingerprint().equals(fingerprint)) throw new ResponseStatusException(HttpStatus.CONFLICT,"网页已有新版本，请刷新后审核");
        return c;
    }
}
