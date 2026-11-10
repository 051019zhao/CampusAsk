package com.campusask.campusask.crawler;

import org.springframework.stereotype.Component;
import java.net.*;
import java.util.*;

/** All requests, including robots.txt, pass through this boundary. Redirects are not followed. */
@Component
public class CrawlPolicy {
    private final CrawlerProperties config;
    public CrawlPolicy(CrawlerProperties config) { this.config=config; }
    public URI canonical(String url) {
        if(url.length()>1000) throw new IllegalArgumentException("地址过长");
        URI uri=URI.create(url).normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || (uri.getPort()!=-1 && uri.getPort()!=443)) throw new IllegalArgumentException("仅允许 HTTPS 官网地址");
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        if (config.getAllowedHosts().stream().noneMatch(h -> host.equalsIgnoreCase(h))) throw new IllegalArgumentException("地址不在抓取白名单");
        try { return new URI("https",null,host,-1,uri.getPath().isEmpty()?"/":uri.getPath(),uri.getQuery(),null); }
        catch (URISyntaxException e) { throw new IllegalArgumentException("地址格式不正确",e); }
    }
    public void validateNetwork(URI uri) throws UnknownHostException {
        canonical(uri.toString());
        for (InetAddress address:InetAddress.getAllByName(uri.getHost())) {
            byte[] bytes=address.getAddress();
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || (bytes.length==16 && (bytes[0]&0xfe)==0xfc))
                throw new IllegalArgumentException("拒绝访问非公开网络地址");
        }
    }
}
