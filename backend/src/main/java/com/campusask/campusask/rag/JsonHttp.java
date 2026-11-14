package com.campusask.campusask.rag;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Small synchronous HTTP helper; no selector thread is needed for short JSON calls. */
final class JsonHttp {
    record Response(int status,String body) {}
    private JsonHttp() {}
    static Response post(URI uri,byte[] body,Map<String,String> headers,int timeoutMillis) throws java.io.IOException {
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(Math.min(5000,timeoutMillis));
            connection.setReadTimeout(timeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json");
            for(var header:headers.entrySet()) connection.setRequestProperty(header.getKey(),header.getValue());
            try(var output=connection.getOutputStream()) { output.write(body); }
            int status=connection.getResponseCode();
            InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();
            String text;
            if(stream==null) text="";
            else try(stream) { text=new String(stream.readAllBytes(),StandardCharsets.UTF_8); }
            return new Response(status,text);
        } finally { connection.disconnect(); }
    }
}
