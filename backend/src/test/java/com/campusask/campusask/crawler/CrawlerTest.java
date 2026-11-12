package com.campusask.campusask.crawler;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CrawlerTest {
    private CrawlerProperties config;
    private CrawlPolicy policy;
    private CrawlExtractor extractor;
    private CrawlStore store;
    private JdbcTemplate db;
    private final URI url=URI.create("https://campus.example.edu/news/a.html");
    @BeforeEach void setup() {
        config=new CrawlerProperties();config.setAllowedHosts(List.of("campus.example.edu"));config.setMinContentLength(10);
        policy=new CrawlPolicy(config);extractor=new CrawlExtractor(config);
        DriverManagerDataSource source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(source);store=new CrawlStore(db,new DataSourceTransactionManager(source));store.initialize();
        db.execute("CREATE TABLE knowledge(id BIGINT AUTO_INCREMENT PRIMARY KEY,title VARCHAR(500),content TEXT,category VARCHAR(50),keywords VARCHAR(200),view_count INT,created_by BIGINT,created_at TIMESTAMP)");
    }
    private CrawlExtractor.Article article(String body) {return new CrawlExtractor.Article(url.toString(),"校园公告",body,"hash-"+body,List.of());}
    @SuppressWarnings("unchecked") private CrawlStore.Candidate first(String status) { return ((List<CrawlStore.Candidate>)store.candidates(status,1).get("items")).get(0); }
    @Test void canonicalizationEnforcesExactHostHttpsPortAndNoCredentials() {
        assertEquals("https://campus.example.edu/news/a.html",policy.canonical(url+"#tracking").toString());
        for(String bad:List.of("http://campus.example.edu/a","https://campus.example.edu.evil.test/a","https://user@campus.example.edu/a","https://campus.example.edu:8080/a","https://localhost/a"))
            assertThrows(IllegalArgumentException.class,()->policy.canonical(bad));
    }
    @Test void privateNetworkIsRejectedEvenWhenAllowlisted() {
        config.setAllowedHosts(List.of("127.0.0.1"));
        assertThrows(IllegalArgumentException.class,()->policy.validateNetwork(URI.create("https://127.0.0.1/")));
    }
    @Test void robotsUsesSpecificGroupsAndLongestAllowRule() {
        RobotsRules rules=new RobotsRules("User-agent: *\nDisallow: /\n\nUser-agent: CampusAskBot\nDisallow: /private\nAllow: /private/public\nDisallow: /*?secret=*$");
        assertTrue(rules.allowed("/news/a.html")); assertFalse(rules.allowed("/private/a")); assertTrue(rules.allowed("/private/public/a")); assertFalse(rules.allowed("/news?secret=123"));
    }
    @Test void robotsMergesGroupsAndAllowWinsTie() {
        RobotsRules rules=new RobotsRules("User-agent: *\nDisallow: /same\n\nUser-agent: *\nAllow: /same\nDisallow: /other");
        assertTrue(rules.allowed("/same")); assertFalse(rules.allowed("/other"));
    }
    @Test void htmlExtractionRemovesNavigationAndKeepsBodyAndSource() throws Exception {
        String html="<html><title>标题</title><body><nav>导航垃圾</nav><main><h1>校园公告</h1><p>这是校园图书馆的有效更新正文内容。</p><script>恶意脚本</script><a href='/news/b.html'>下一条</a></main></body></html>";
        CrawlExtractor.Article result=extractor.extract(url,html.getBytes(StandardCharsets.UTF_8));
        assertTrue(result.content().contains("图书馆"));assertFalse(result.content().contains("导航垃圾"));assertFalse(result.content().contains("恶意脚本"));
        assertEquals(url.toString(),result.url());assertEquals(List.of("https://campus.example.edu/news/b.html"),result.links());
    }
    @Test void noindexAndMissingBodyAreNotStaged() {
        assertThrows(IllegalArgumentException.class,()->extractor.extract(url,"<meta name='robots' content='noindex'><main>正文正文正文正文正文正文</main>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,()->extractor.extract(url,"<title>列表</title>".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void unchangedPagesDoNotCreateDuplicatesOrUndoReview() {
        assertTrue(store.stage(article("有效正文")));CrawlStore.Candidate c=first("PENDING");
        long id=store.approve(c.id(),c.fingerprint(),c.title(),c.content(),"校园公告",1L);
        assertFalse(store.stage(article("有效正文")));assertEquals(id,first("APPROVED").knowledgeId());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM knowledge",Integer.class));
    }
    @Test void changedPagesRequireNewReviewAndOnlyThenUpdatePublishedKnowledge() {
        store.stage(article("旧内容"));CrawlStore.Candidate old=first("PENDING");
        long id=store.approve(old.id(),old.fingerprint(),old.title(),old.content(),"校园公告",1L);
        store.stage(article("新内容"));CrawlStore.Candidate next=first("PENDING");
        assertTrue(db.queryForObject("SELECT content FROM knowledge WHERE id=?",String.class,id).contains("旧内容"));
        assertEquals(id,store.approve(next.id(),next.fingerprint(),next.title(),next.content(),"校园公告",2L));
        assertTrue(db.queryForObject("SELECT content FROM knowledge WHERE id=?",String.class,id).contains("新内容"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM knowledge",Integer.class));
    }
    @Test void staleReviewsCannotPublishOrRejectNewVersion() {
        store.stage(article("旧内容"));CrawlStore.Candidate old=first("PENDING");store.stage(article("新内容"));
        assertThrows(ResponseStatusException.class,()->store.approve(old.id(),old.fingerprint(),old.title(),old.content(),"公告",1L));
        assertThrows(ResponseStatusException.class,()->store.reject(old.id(),old.fingerprint(),1L));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM knowledge",Integer.class));
    }
    @Test void publishingIsIdempotentAndPreservesSourceAndReviewer() {
        store.stage(article("有效正文"));CrawlStore.Candidate c=first("PENDING");
        long first=store.approve(c.id(),c.fingerprint(),c.title(),c.content(),"公告",7L);
        assertEquals(first,store.approve(c.id(),c.fingerprint(),c.title(),c.content(),"公告",7L));
        assertTrue(db.queryForObject("SELECT content FROM knowledge WHERE id=?",String.class,first).contains(url.toString()));
        assertEquals(7,db.queryForObject("SELECT reviewed_by FROM crawl_candidate WHERE id=?",Integer.class,c.id()));
    }
    @Test void rejectedVersionStaysRejectedUntilContentChanges() {
        store.stage(article("旧内容"));CrawlStore.Candidate c=first("PENDING");store.reject(c.id(),c.fingerprint(),1L);
        assertFalse(store.stage(article("旧内容")));assertEquals(c.id(),first("REJECTED").id());
        assertThrows(ResponseStatusException.class,()->store.approve(c.id(),c.fingerprint(),c.title(),c.content(),"公告",1L));
        assertTrue(store.stage(article("新内容")));assertEquals(c.id(),first("PENDING").id());
    }
    @Test void releasedLeaseUsesMysqlSafeTimestamp() {
        java.time.Instant lowerBound=java.time.Instant.parse("1971-01-01T00:00:00Z");
        java.sql.Timestamp initial=db.queryForObject("SELECT expires_at FROM crawl_lease WHERE id=1",java.sql.Timestamp.class);
        assertTrue(initial.toInstant().isAfter(lowerBound));
        assertTrue(store.acquire("run-1"));
        store.release("run-1");
        java.sql.Timestamp released=db.queryForObject("SELECT expires_at FROM crawl_lease WHERE id=1",java.sql.Timestamp.class);
        assertTrue(released.toInstant().isAfter(lowerBound));
        assertTrue(store.acquire("run-2"));
    }
    @Test void databaseLeasePreventsOverlapAndCannotBeReleasedByOtherOwner() {
        assertTrue(store.acquire("run-1"));assertFalse(store.acquire("run-2"));store.release("run-2");assertFalse(store.acquire("run-2"));store.release("run-1");assertTrue(store.acquire("run-2"));
    }
    @Test void failedPublishRollsBackReviewState() {
        store.stage(article("有效正文"));CrawlStore.Candidate c=first("PENDING");
        assertThrows(IllegalArgumentException.class,()->store.approve(c.id(),c.fingerprint(),c.title(),"长".repeat(5000),"公告",1L));
        assertEquals(c.id(),first("PENDING").id());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM knowledge",Integer.class));
    }
    @Test void robotsDenialNeverFetchesPage() throws Exception {
        CrawlFetcher fetcher=mock(CrawlFetcher.class);when(fetcher.fetch(any())).thenReturn(new CrawlFetcher.Page(200,"text/plain","User-agent: *\nDisallow: /".getBytes(StandardCharsets.UTF_8)));
        try(CloseableCrawler runner=new CloseableCrawler(new CrawlerService(config,policy,fetcher,extractor,store))) {
            store.start("test-run");runner.service.run("test-run",List.of(url));
            verify(fetcher,times(1)).fetch(any());assertEquals(0L,store.candidates("PENDING",1).get("total"));
        }
    }
    @Test void byteLimitCancelsOversizedResponses() {
        CrawlFetcher.LimitedBody body=new CrawlFetcher.LimitedBody();Flow.Subscription subscription=mock(Flow.Subscription.class);body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[2*1024*1024+1])));
        verify(subscription).cancel();assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
    }
    private record CloseableCrawler(CrawlerService service) implements AutoCloseable { public void close() {service.close();} }
}
