package com.campusask.campusask.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {
    private static final Logger log=LoggerFactory.getLogger(RequestTraceFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String trace=UUID.randomUUID().toString(); long started=System.nanoTime();
        response.setHeader("X-Request-ID",trace); MDC.put("requestId",trace);
        try { chain.doFilter(request,response); }
        finally {
            // Deliberately exclude query parameters, bodies, credentials and user identity.
            log.info("request_complete method={} status={} durationMs={}",request.getMethod(),response.getStatus(),(System.nanoTime()-started)/1_000_000);
            MDC.remove("requestId");
        }
    }
}
