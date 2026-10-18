package com.liushao.config;

import com.liushao.dao.UmsUserDao;
import com.liushao.security.CommunitySessions;
import com.liushao.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtUtil jwt;
  private final UmsUserDao users;
  private final CommunitySessions sessions;
  private final String adminKey;

  public JwtAuthenticationFilter(JwtUtil jwt, UmsUserDao users, CommunitySessions sessions) {
    this(jwt, users, sessions, "");
  }

  public JwtAuthenticationFilter(
      JwtUtil jwt, UmsUserDao users, CommunitySessions sessions, String adminKey) {
    this.adminKey = adminKey;
    this.jwt = jwt;
    this.users = users;
    this.sessions = sessions;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getServletPath(), method = request.getMethod();
    if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
    path = path.replaceAll(";[^/]*", "");
    if (path.equals("/internal/admin") || path.startsWith("/internal/admin/")) {
      String supplied = request.getHeader("X-Community-Admin-Key");
      if (adminKey == null
          || adminKey.length() < 32
          || supplied == null
          || supplied.length() > 512
          || !java.security.MessageDigest.isEqual(digest(adminKey), digest(supplied))) {
        error(response, 403, "内部管理接口未授权");
        return;
      }
      String actor = request.getHeader("X-Community-Admin-Actor");
      try {
        actor =
            java.net.URLDecoder.decode(
                Objects.toString(actor, ""), java.nio.charset.StandardCharsets.UTF_8);
        if (actor.isBlank()
            || actor.length() > 64
            || actor.chars().anyMatch(Character::isISOControl))
          throw new IllegalArgumentException();
      } catch (IllegalArgumentException e) {
        error(response, 400, "缺少有效操作人");
        return;
      }
      request.setAttribute("internalAdminActor", actor);
      response.setHeader("Cache-Control", "no-store");
      chain.doFilter(request, response);
      return;
    }
    boolean authEndpoint =
        Set.of(
                "/ums/user/login",
                "/ums/user/register",
                "/ums/user/password/forgot",
                "/ums/user/password/reset")
            .contains(path);
    boolean protectedPath =
        path.equals("/me")
            || path.startsWith("/me/")
            || path.equals("/notifications")
            || path.startsWith("/notifications/")
            || path.equals("/messages")
            || path.startsWith("/messages/")
            || path.matches("/post/[^/]+/interaction")
            || path.matches("/users/[^/]+/relationship")
            || Set.of(
                    "/ums/user/info",
                    "/ums/user/logout",
                    "/post/add",
                    "/post/create",
                    "/post/update")
                .contains(path)
            || (!Set.of("GET", "HEAD", "OPTIONS").contains(method) && !authEndpoint);
    if ("OPTIONS".equals(method) || !protectedPath) {
      chain.doFilter(request, response);
      return;
    }
    final String username;
    try {
      String token = request.getHeader("Authorization");
      if (token == null || token.isBlank()) throw new IllegalArgumentException();
      var claims = jwt.getClaimByToken(token.startsWith("Bearer ") ? token.substring(7) : token);
      username = claims.getSubject();
      var user = users.findByUsername(username);
      Number version = claims.get("version", Number.class);
      if (user == null
          || !Boolean.TRUE.equals(user.getStatus())
          || version == null
          || version.longValue() != user.getAuthVersion()
          || !sessions.active(claims.getId(), username)) throw new IllegalArgumentException();
      request.setAttribute("authenticatedUsername", username);
      request.setAttribute("sessionId", claims.getId());
      response.setHeader("Cache-Control", "no-store");
    } catch (JwtException | IllegalArgumentException e) {
      error(response, 401, "登录已失效，请重新登录");
      return;
    } catch (Exception e) {
      error(response, 503, "认证服务暂时不可用");
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          @Override
          public String getHeader(String name) {
            return JwtUtil.USER_NAME.equalsIgnoreCase(name) ? username : super.getHeader(name);
          }

          @Override
          public Enumeration<String> getHeaders(String name) {
            return JwtUtil.USER_NAME.equalsIgnoreCase(name)
                ? Collections.enumeration(List.of(username))
                : super.getHeaders(name);
          }
        },
        response);
  }

  private static byte[] digest(String value) {
    try {
      return java.security.MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private void error(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response
        .getWriter()
        .write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
  }
}
