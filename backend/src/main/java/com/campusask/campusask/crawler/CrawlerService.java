package com.campusask.campusask.crawler;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CrawlerService {
    private final CrawlerProperties config;
    private final CrawlPolicy policy;
    private final CrawlFetcher fetcher;
    private final CrawlExtractor extractor;
    private final CrawlStore store;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"campus-crawler");t.setDaemon(true);return t;});
    public CrawlerService(CrawlerProperties config,CrawlPolicy policy,CrawlFetcher fetcher,CrawlExtractor extractor,CrawlStore store) {
        this.config=config;this.policy=policy;this.fetcher=fetcher;this.extractor=extractor;this.store=store;
    }
    @Scheduled(cron="${crawler.cron:0 30 2 * * *}",zone="${crawler.zone:Asia/Shanghai}")
    public void scheduled() { if(config.isEnabled()) try { trigger(); } catch(ResponseStatusException ignored) { /* Existing run owns the lease. */ } }
    public String trigger() {
        if(!config.isEnabled()||config.getSeeds().isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"请先启用爬虫并配置官网种子地址");
        List<URI> seeds=config.getSeeds().stream().map(policy::canonical).toList();
        if(!running.compareAndSet(false,true)) throw new ResponseStatusException(HttpStatus.CONFLICT,"已有抓取任务正在执行");
        String id=UUID.randomUUID().toString();
        try {
            if(!store.acquire(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"其他实例已有抓取任务正在执行");
            store.start(id); worker.submit(()->{
                try {run(id,seeds);} catch(RuntimeException e) {org.slf4j.LoggerFactory.getLogger(CrawlerService.class).error("crawler_run_failed runId={} type={}",id,e.getClass().getSimpleName());}
            }); return id;
        } catch(RuntimeException e) { running.set(false); store.release(id); throw e; }
    }
    void run(String id,List<URI> seeds) {
        int visited=0,changed=0,failed=0; String detail="";
        Deque<URI> queue=new ArrayDeque<>(seeds); Set<URI> seen=new HashSet<>(); Map<String,RobotsRules> robots=new HashMap<>();
        try {
            while(!queue.isEmpty()&&visited<config.getMaxPages()&&!Thread.currentThread().isInterrupted()) {
                URI uri=queue.removeFirst(); if(!seen.add(uri)) continue; visited++;
                try {
                    RobotsRules rules=robots.get(uri.getHost());
                    if(rules==null) {
                        robots.put(uri.getHost(),new RobotsRules("User-agent: *\nDisallow: /"));
                        CrawlFetcher.Page response=fetcher.fetch(policy.canonical("https://"+uri.getHost()+"/robots.txt"));
                        if(response.status()==404) rules=new RobotsRules("");
                        else if(response.status()==200) rules=new RobotsRules(new String(response.body(),StandardCharsets.UTF_8));
                        else throw new IllegalArgumentException("robots.txt 不可用，已跳过该站点");
                        robots.put(uri.getHost(),rules);
                    }
                    URI ascii=URI.create(uri.toASCIIString());
                    if(!rules.allowed(ascii.getRawPath()+(ascii.getRawQuery()==null?"":"?"+ascii.getRawQuery()))) throw new IllegalArgumentException("robots.txt 禁止抓取");
                    CrawlFetcher.Page page=fetcher.fetch(uri);
                    if(page.status()!=200 || !page.contentType().toLowerCase(Locale.ROOT).contains("text/html")) throw new IllegalArgumentException("页面状态异常或不是 HTML");
                    for(String link:extractor.links(uri,page.body())) {
                        if(queue.size()>=100) break;
                        try { URI next=policy.canonical(link); if(!seen.contains(next)) queue.addLast(next); } catch(IllegalArgumentException ignored) { /* Out-of-scope link. */ }
                    }
                    if(extractor.isListingPage(uri)) continue;
                    CrawlExtractor.Article article=extractor.extract(uri,page.body());
                    if(store.stage(article)) changed++;
                } catch(InterruptedException e) { Thread.currentThread().interrupt(); failed++; detail="任务已中断"; break; }
                catch(Exception e) { failed++; detail=uri.getHost()+"："+e.getClass().getSimpleName()+"（请检查页面或抓取配置）"; }
            }
        } finally {
            try { store.finish(id,visited,changed,failed,detail); }
            finally { try { store.release(id); } finally { running.set(false); } }
        }
    }
    public Map<String,Object> status() { return Map.of("enabled",config.isEnabled(),"running",running.get(),"seeds",config.getSeeds(),"runs",store.runs()); }
    @PreDestroy public void close() { worker.shutdownNow(); }
}
