package com.smartcart.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Component
public class AuthRateLimit extends OncePerRequestFilter {
    private record Window(long start,int count) {}
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
    private final boolean production;
    public AuthRateLimit(Environment environment){production=environment.acceptsProfiles(Profiles.of("prod"));}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){
        if(!"POST".equals(r.getMethod()))return true;
        String path=r.getServletPath();
        if(path.isEmpty())path=r.getRequestURI().substring(r.getContextPath().length());
        return !path.startsWith("/api/auth/")&&!(production&&path.startsWith("/api/location-lookup/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException {
        long now=System.currentTimeMillis();
        if(windows.size()>1000)windows.entrySet().removeIf(e->now-e.getValue().start>=60000);
        String path=r.getServletPath().isEmpty()?r.getRequestURI():r.getServletPath();
        String bucket=path.startsWith("/api/location-lookup/")?"location":path.matches(".*/auth/(login|register|demo|forgot-password|reset-password)/?")?"credential":"session";
        int limit=production&&!"session".equals(bucket)?15:60;
        String key=bucket+":"+r.getRemoteAddr();
        if(windows.size()>=10000&&!windows.containsKey(key)){reject(s);return;}
        Window w=windows.compute(key,(k,v)->v==null||now-v.start>=60000?new Window(now,1):new Window(v.start,Math.min(limit+1,v.count+1)));
        if(w.count>limit){reject(s);return;}
        chain.doFilter(r,s);
    }

    private static void reject(HttpServletResponse response)throws IOException{
        response.setStatus(429);
        response.setHeader("Retry-After","60");
        response.setHeader("Cache-Control","no-store");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"RATE_LIMIT\",\"message\":\"Please wait a minute before trying again.\"}");
    }
}
