package com.liushao.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.liushao.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterTest {
  private final JwtUtil jwt =
      new JwtUtil(
          "test-only-secret-abcdefghijklmnopqrstuvwxyz-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ", 60);
  private final com.liushao.dao.UmsUserDao users = mock(com.liushao.dao.UmsUserDao.class);
  private final com.liushao.security.CommunitySessions sessions =
      mock(com.liushao.security.CommunitySessions.class);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users, sessions);

  @Test
  void missingTokenCannotAccessUserInfo() throws Exception {
    for (String path : new String[] {"/ums/user/info", "/post/add"}) {
      var request = new MockHttpServletRequest();
      request.setServletPath(path);
      var response = new MockHttpServletResponse();
      var chain = mock(FilterChain.class);
      filter.doFilter(request, response, chain);
      assertEquals(401, response.getStatus());
      verifyNoInteractions(chain);
    }
  }

  @Test
  void validatedIdentityReplacesForgedHeader() throws Exception {
    var user = new com.liushao.model.entity.UmsUser();
    user.setUsername("alice");
    when(users.findByUsername("alice")).thenReturn(user);
    when(sessions.active(anyString(), eq("alice"))).thenReturn(true);
    var request = new MockHttpServletRequest();
    request.setServletPath("/ums/user/info");
    request.addHeader("Authorization", "Bearer " + jwt.generateToken("alice"));
    request.addHeader("userName", "administrator");
    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          var authenticated = (HttpServletRequest) req;
          assertEquals("alice", authenticated.getHeader("userName"));
          assertEquals("alice", authenticated.getHeaders("userName").nextElement());
        });
  }

  @Test
  void publicRequestsAndPreflightAreAllowed() throws Exception {
    for (String method : new String[] {"GET", "OPTIONS"}) {
      var request = new MockHttpServletRequest();
      request.setMethod(method);
      request.setServletPath(method.equals("GET") ? "/post/list" : "/ums/user/info");
      var response = new MockHttpServletResponse();
      var chain = mock(FilterChain.class);
      filter.doFilter(request, response, chain);
      verify(chain).doFilter(request, response);
    }
  }
}
