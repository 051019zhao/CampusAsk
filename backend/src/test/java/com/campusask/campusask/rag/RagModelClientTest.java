package com.campusask.campusask.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual HTTP client against an isolated local model stub. */
class RagModelClientTest {
    private HttpServer server;
    private RagModelClient client;
    private String response;
    private int status;
    private AtomicReference<String> request;
    @BeforeEach void setup() throws Exception {
        response=""; status=200; request=new AtomicReference<>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/model",exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); client=new RagModelClient();
        String url="http://127.0.0.1:"+server.getAddress().getPort()+"/model";
        ReflectionTestUtils.setField(client,"embeddingUrl",url);
        ReflectionTestUtils.setField(client,"embeddingKey","");
        ReflectionTestUtils.setField(client,"embeddingModel","test-vector");
        ReflectionTestUtils.setField(client,"chatUrl",url);
        ReflectionTestUtils.setField(client,"chatKey","test-only-key");
        ReflectionTestUtils.setField(client,"chatModel","test-chat");
    }
    @AfterEach void teardown() { server.stop(0); }
    @Test void embeddingParsesVectorAndSerializesUnicodeQuotes() {
        response="{\"data\":[{\"embedding\":[0.5,1.0]}]}";
        assertArrayEquals(new double[]{.5,1},client.embed("校园\"资料\n下一行"));
        assertTrue(request.get().contains("\\\"资料\\n下一行"));
    }
    @Test void answerPreservesQuotesAndNewlines() {
        response="{\"choices\":[{\"message\":{\"content\":\"第一行\\n\\\"第二行\\\" [S1]\"}}]}";
        assertEquals("第一行\n\"第二行\" [S1]",client.answer("问题","[S1] 资料"));
        assertTrue(request.get().contains("\"max_tokens\":1200"));
    }
    @Test void modelErrorsAreNotAnswers() {
        status=429; response="{\"error\":\"rate limited\"}";
        assertThrows(IllegalStateException.class,()->client.embed("校园"));
    }
    @Test void emptyAndZeroVectorsAreRejected() {
        response="{\"data\":[{\"embedding\":[]}]}";
        assertThrows(IllegalStateException.class,()->client.embed("校园"));
        response="{\"data\":[{\"embedding\":[0,0]}]}";
        assertThrows(IllegalStateException.class,()->client.embed("校园"));
    }
    @Test void emptyChatResponseIsRejected() {
        response="{\"choices\":[]}";
        assertThrows(IllegalStateException.class,()->client.answer("校园","资料"));
    }
}
