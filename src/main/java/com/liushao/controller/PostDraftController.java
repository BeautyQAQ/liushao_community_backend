package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.dto.DraftRequests;
import com.liushao.model.vo.*;
import com.liushao.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/me/drafts")
public class PostDraftController {
  private final PostDraftService drafts;
  private final UmsUserService users;

  public PostDraftController(PostDraftService drafts, UmsUserService users) {
    this.drafts = drafts;
    this.users = users;
  }

  @GetMapping
  public ApiResult<PageResult<Map<String, Object>>> list(
      @RequestAttribute("authenticatedUsername") String username,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(drafts.list(users.requireUser(username), pageNo, size));
  }

  @GetMapping("/{id}")
  public ApiResult<PostDraft> get(
      @PathVariable UUID id, @RequestAttribute("authenticatedUsername") String username) {
    return ApiResult.success(drafts.get(id, users.requireUser(username)));
  }

  @PutMapping("/{id}")
  public ApiResult<PostDraft> save(
      @PathVariable UUID id, @RequestAttribute("authenticatedUsername") String username,
      @Valid @RequestBody DraftRequests.Save input) {
    return ApiResult.success(drafts.save(id, users.requireUser(username), input));
  }

  @DeleteMapping("/{id}")
  public ApiResult<Void> delete(
      @PathVariable UUID id, @RequestAttribute("authenticatedUsername") String username,
      @Valid @RequestBody DraftRequests.Revision input) {
    drafts.delete(id, users.requireUser(username), input.version());
    return ApiResult.success(null);
  }

  @PostMapping("/{id}/publish")
  public ApiResult<PostDetail> publish(
      @PathVariable UUID id, @RequestAttribute("authenticatedUsername") String username,
      @Valid @RequestBody DraftRequests.Revision input) {
    return ApiResult.success(drafts.publish(id, users.requireUser(username), input.version()));
  }
}
