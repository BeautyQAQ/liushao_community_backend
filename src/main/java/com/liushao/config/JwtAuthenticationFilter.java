package com.liushao.config;

import com.liushao.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwt;
    private static final List<String> PROTECTED = List.of("/ums/user/info", "/post/create", "/post/add", "/post/update", "/post/delete/*", "/comment/add_comment", "/relationship/**");
    public JwtAuthenticationFilter(JwtUtil jwt) { this.jwt = jwt; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if ("OPTIONS".equals(request.getMethod()) || PROTECTED.stream().noneMatch(path -> new AntPathMatcher().match(path, request.getServletPath()))) {
            chain.doFilter(request, response);
            return;
        }
        final String username;
        try {
            String token = request.getHeader("Authorization");
            if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing token");
            username = jwt.getClaimByToken(token.startsWith("Bearer ") ? token.substring(7) : token).getSubject();
            if (username == null || username.isBlank()) throw new IllegalArgumentException("Missing subject");
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"登录已失效，请重新登录\",\"data\":null}");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public String getHeader(String name) { return JwtUtil.USER_NAME.equalsIgnoreCase(name) ? username : super.getHeader(name); }
            @Override public Enumeration<String> getHeaders(String name) { return JwtUtil.USER_NAME.equalsIgnoreCase(name) ? Collections.enumeration(List.of(username)) : super.getHeaders(name); }
        }, response);
    }
}
