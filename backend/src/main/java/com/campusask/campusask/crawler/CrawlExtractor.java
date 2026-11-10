package com.campusask.campusask.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class CrawlExtractor {
    public record Article(String url,String title,String content,String fingerprint,List<String> links) {}
    private final CrawlerProperties config;
    public CrawlExtractor(CrawlerProperties config) { this.config=config; }
    public List<String> links(URI uri,byte[] bytes) throws IOException {
        return Jsoup.parse(new ByteArrayInputStream(bytes),null,uri.toString()).select("a[href]").stream()
                .map(a->a.absUrl("href")).filter(s->s.matches("(?i)https://.*\\.html?(?:[?#].*)?$")).distinct().limit(100).toList();
    }
    public Article extract(URI uri,byte[] bytes) throws IOException {
        Document doc=Jsoup.parse(new ByteArrayInputStream(bytes),null,uri.toString());
        if(!doc.select("meta[name=robots][content~=(?i)(noindex|noarchive)]").isEmpty())
            throw new IllegalArgumentException("页面声明不允许索引或归档");
        List<String> links=doc.select("a[href]").stream().map(a->a.absUrl("href"))
                .filter(s->s.matches("(?i)https://.*\\.html?(?:[?#].*)?$")).distinct().limit(100).toList();
        doc.select("script,style,nav,header,footer,aside,form,noscript").remove();
        Element main=doc.selectFirst(config.getContentSelector());
        if(main==null) throw new IllegalArgumentException("未找到正文区域，请配置该站点的正文选择器");
        String title=Optional.ofNullable(doc.selectFirst("h1")).map(Element::text).filter(s->!s.isBlank()).orElse(doc.title());
        String text=main.wholeText().replaceAll("[ \\t\\x0B\\f]+"," ").replaceAll("\\n{3,}","\n\n").trim();
        if(title.isBlank() || text.length()<config.getMinContentLength()) throw new IllegalArgumentException("正文过短，跳过列表或空页面");
        if(text.length()>100_000) throw new IllegalArgumentException("正文超过 10 万字符");
        try {
            String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((title+"\n"+text).getBytes(StandardCharsets.UTF_8)));
            return new Article(uri.toString(),title.substring(0,Math.min(255,title.length())),text,fingerprint,links);
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
