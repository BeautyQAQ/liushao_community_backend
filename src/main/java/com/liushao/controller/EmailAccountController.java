package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.common.exception.ApiException;
import com.liushao.security.CommunitySessions;
import com.liushao.service.EmailAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ums/user")
public class EmailAccountController {
  public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=128) String email) {}
  public record Code(@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
  public record Reset(@NotBlank @jakarta.validation.constraints.Email @Size(max=128) String email,
      @NotBlank @Pattern(regexp="[0-9]{6}") String code, @NotBlank @Size(min=8,max=72) String password,
      @NotBlank @Size(min=8,max=72) String checkPass) {}
  private final EmailAccountService service;
  private final CommunitySessions sessions;
  public EmailAccountController(EmailAccountService service, CommunitySessions sessions) { this.service=service; this.sessions=sessions; }

  @PostMapping("/email/send")
  public ApiResult<Void> send(@RequestAttribute("authenticatedUsername") String user, HttpServletRequest request) {
    limit("email-send:"+request.getRemoteAddr(),5);
    service.sendVerification(user);
    return ApiResult.success();
  }
  @PostMapping("/email/verify")
  public ApiResult<Void> verify(@RequestAttribute("authenticatedUsername") String user, @Valid @RequestBody Code code) {
    limit("email-verify:"+user,10);
    service.verify(user,code.code());
    return ApiResult.success();
  }
  @PostMapping("/password/forgot")
  public ApiResult<Void> forgot(@Valid @RequestBody Email input, HttpServletRequest request) {
    limit("password-forgot:"+request.getRemoteAddr(),5);
    service.forgot(input.email());
    return ApiResult.success();
  }
  @PostMapping("/password/reset")
  public ApiResult<Void> reset(@Valid @RequestBody Reset input, HttpServletRequest request) {
    limit("password-reset:"+request.getRemoteAddr(),10);
    service.reset(input.email(),input.code(),input.password(),input.checkPass());
    return ApiResult.success();
  }
  private void limit(String key,int count) { if (!sessions.allow(key,count)) throw new ApiException(429,"操作过于频繁，请一分钟后再试"); }
}
