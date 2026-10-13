package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.dto.CreateTopicDTO;
import com.liushao.model.vo.*;
import com.liushao.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/post")
public class BmsPostController {
  private final BmsPostService posts;
  private final UmsUserService users;

  public BmsPostController(BmsPostService posts, UmsUserService users) {
    this.posts = posts;
    this.users = users;
  }

  @GetMapping("/list")
  public ApiResult<PageResult<PostVO>> list(
      @RequestParam(defaultValue = "latest") String tab,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "") String tag,
      @RequestParam(defaultValue = "") String author) {
    return ApiResult.success(PageResult.from(posts.getList(tab, pageNo, size, query, tag, author)));
  }

  @GetMapping("/{id}")
  public ApiResult<PostDetail> detail(@PathVariable int id) {
    return ApiResult.success(posts.detail(id));
  }

  @PostMapping("/add")
  public ApiResult<PostDetail> add(
      @RequestAttribute("authenticatedUsername") String username,
      @Valid @RequestBody CreateTopicDTO input) {
    return ApiResult.success(posts.add(input, users.requireUser(username)));
  }

  @PutMapping("/{id}")
  public ApiResult<PostDetail> update(
      @PathVariable int id,
      @RequestAttribute("authenticatedUsername") String username,
      @Valid @RequestBody CreateTopicDTO input) {
    return ApiResult.success(posts.update(id, input, users.requireUser(username)));
  }

  @DeleteMapping("/{id}")
  public ApiResult<Void> delete(
      @PathVariable int id, @RequestAttribute("authenticatedUsername") String username) {
    posts.delete(id, users.requireUser(username));
    return ApiResult.success();
  }
}
