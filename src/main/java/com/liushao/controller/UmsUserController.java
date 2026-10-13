package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.common.exception.ApiException;
import com.liushao.model.dto.*;
import com.liushao.model.entity.UmsUser;
import com.liushao.security.CommunitySessions;
import com.liushao.service.UmsUserService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ums/user")
public class UmsUserController {
  private final UmsUserService users;
  private final CommunitySessions sessions;

  public UmsUserController(UmsUserService users, CommunitySessions sessions) {
    this.users = users;
    this.sessions = sessions;
  }

  @PostMapping("/register")
  public ApiResult<UmsUser> register(
      @Valid @RequestBody RegisterDTO input, HttpServletRequest request) {
    if (!sessions.allow("register:" + request.getRemoteAddr(), 10))
      throw new ApiException(429, "注册过于频繁，请一分钟后再试");
    return ApiResult.success(users.executeRegister(input));
  }

  @PostMapping("/login")
  public ApiResult<Map<String, String>> login(
      @Valid @RequestBody LoginDTO input,
      HttpServletRequest request,
      HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    if (!sessions.allow("login:" + request.getRemoteAddr(), 20))
      throw new ApiException(429, "登录过于频繁，请一分钟后再试");
    String token = users.executeLogin(input);
    if (token == null) throw new ApiException(401, "账号或密码错误");
    return ApiResult.success(Map.of("token", token));
  }

  @GetMapping("/info")
  public ApiResult<UmsUser> info(@RequestAttribute("authenticatedUsername") String username) {
    return ApiResult.success(users.requireUser(username));
  }

  @PostMapping("/logout")
  public ApiResult<Void> logout(@RequestAttribute("sessionId") String id) {
    sessions.revoke(id);
    return ApiResult.success();
  }

  @GetMapping("/profile/{username}")
  public ApiResult<Map<String, Object>> profile(@PathVariable String username) {
    return ApiResult.success(users.publicProfile(username));
  }

  @PostMapping("/profile")
  public ApiResult<UmsUser> updateProfile(
      @RequestAttribute("authenticatedUsername") String username,
      @Valid @RequestBody ProfileRequests.Profile input) {
    return ApiResult.success(users.updateProfile(username, input));
  }

  @PostMapping("/password")
  public ApiResult<Void> password(
      @RequestAttribute("authenticatedUsername") String username,
      @Valid @RequestBody ProfileRequests.Password input) {
    users.password(username, input);
    return ApiResult.success();
  }
}
