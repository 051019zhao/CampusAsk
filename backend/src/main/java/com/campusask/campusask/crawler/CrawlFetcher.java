package com.campusask.campusask.crawler;

import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

@Component
public class CrawlFetcher {
    public record Page(int status,String contentType,byte[] body) {}
    private final CrawlPolicy policy;
    private final CrawlerProperties config;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private long lastRequest;
    public CrawlFetcher(CrawlPolicy policy,CrawlerProperties config) { this.policy=policy; this.config=config; }
    public synchronized Page fetch(URI uri) throws IOException,InterruptedException {
        policy.validateNetwork(uri);
        long wait=config.getDelayMillis()-(System.nanoTime()-lastRequest)/1_000_000;
        if(wait>0) Thread.sleep(wait);
        lastRequest=System.nanoTime();
        HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .header("User-Agent","CampusAskBot/1.0 (+campus knowledge maintenance)")
                .header("Accept","text/html,text/plain;q=0.9").GET().build();
        HttpResponse<byte[]> response=http.send(request,info->new LimitedBody());
        return new Page(response.statusCode(),response.headers().firstValue("Content-Type").orElse(""),response.body());
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription;subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(ByteBuffer buffer:buffers) {
                if(output.size()+buffer.remaining()>2*1024*1024) {
                    subscription.cancel();result.completeExceptionally(new IOException("页面超过 2 MB"));return;
                }
                byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);output.writeBytes(bytes);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(output.toByteArray()); }
    }
}
