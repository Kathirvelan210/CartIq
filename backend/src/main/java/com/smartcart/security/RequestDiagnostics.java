package com.smartcart.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestDiagnostics extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestDiagnostics.class);
    private static final Set<String> METHODS = Set.of("GET","POST","PUT","PATCH","DELETE","OPTIONS","HEAD");

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        String id=UUID.randomUUID().toString();
        response.setHeader("X-Request-Id",id);
        long start=System.nanoTime();
        MDC.put("requestId",id);
        boolean completed=false;
        try {
            chain.doFilter(request,response);
            completed=true;
        } finally {
            try {
                int status=completed?response.getStatus():500;
                String method=METHODS.contains(request.getMethod())?request.getMethod():"OTHER";
                Object route=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                if(status>=400||!"/actuator/health/liveness".equals(route)&&!"/actuator/health/readiness".equals(route)) {
                    LOG.info("http_request requestId={} method={} route={} status={} durationMs={}",id,method,
                        route==null?"unmapped":route,status,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
                }
            } finally {
                MDC.remove("requestId");
            }
        }
    }
}
