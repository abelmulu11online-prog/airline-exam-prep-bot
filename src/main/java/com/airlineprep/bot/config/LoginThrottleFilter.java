package com.airlineprep.bot.config;

import java.io.IOException;
import java.time.*;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded per-peer login budget. Never trusts forwarded headers or locks a username. */
public final class LoginThrottleFilter extends OncePerRequestFilter {
    private record Window(Instant start,int attempts) {}
    private final Map<String,Window> peers=new LinkedHashMap<>();
    private final Clock clock;
    public LoginThrottleFilter() { this(Clock.systemUTC()); }
    LoginThrottleFilter(Clock clock) { this.clock=clock; }
    synchronized boolean allow(String peer) {
        Instant now=clock.instant();
        peers.values().removeIf(window -> !now.isBefore(window.start().plusSeconds(60)));
        Window window=peers.get(peer);
        if(window!=null&&window.attempts()>=10) return false;
        if(window==null&&peers.size()>=4096) return false;
        peers.put(peer,new Window(window==null?now:window.start(),window==null?1:window.attempts()+1));
        return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if(request.getMethod().equals("POST")&&request.getServletPath().equals("/admin/login")&&!allow(request.getRemoteAddr())) {
            response.setStatus(429);response.setHeader("Retry-After","60");response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Too many sign-in attempts. Wait one minute and try again.");return;
        }
        chain.doFilter(request,response);
    }
}
