package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.vo.PageResult;
import com.liushao.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommunitySocialController {
  public record Comment(@NotBlank @Size(max = 2000) String content, @Positive Long parentId) {}

  public record Message(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{2,32}") String username,
      @NotBlank @Size(max = 2000) String content) {}

  public record Counterpart(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{2,32}") String username) {}

  private final CommunitySocialService social;
  private final BmsPostService posts;
  private final UmsUserService users;

  public CommunitySocialController(
      CommunitySocialService social, BmsPostService posts, UmsUserService users) {
    this.social = social;
    this.posts = posts;
    this.users = users;
  }

  @GetMapping("/post/{id}/comments")
  public ApiResult<PageResult<Map<String, Object>>> comments(
      @PathVariable int id,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(social.comments(id, pageNo, size));
  }

  @PostMapping("/post/{id}/comments")
  public ApiResult<Map<String, Object>> comment(
      @PathVariable int id,
      @RequestAttribute("authenticatedUsername") String user,
      @Valid @RequestBody Comment input) {
    return ApiResult.success(social.comment(id, user, input.content(), input.parentId()));
  }

  @DeleteMapping("/comments/{id}")
  public ApiResult<Void> deleteComment(
      @PathVariable long id, @RequestAttribute("authenticatedUsername") String user) {
    social.deleteComment(id, user);
    return ApiResult.success();
  }

  @GetMapping("/post/{id}/interaction")
  public ApiResult<Map<String, Object>> interaction(
      @PathVariable int id, @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.interaction(id, user));
  }

  @PostMapping("/post/{id}/like")
  public ApiResult<Map<String, Object>> like(
      @PathVariable int id, @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.react(id, user, false, true));
  }

  @DeleteMapping("/post/{id}/like")
  public ApiResult<Map<String, Object>> unlike(
      @PathVariable int id, @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.react(id, user, false, false));
  }

  @PostMapping("/post/{id}/bookmark")
  public ApiResult<Map<String, Object>> bookmark(
      @PathVariable int id, @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.react(id, user, true, true));
  }

  @DeleteMapping("/post/{id}/bookmark")
  public ApiResult<Map<String, Object>> unbookmark(
      @PathVariable int id, @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.react(id, user, true, false));
  }

  @GetMapping("/me/bookmarks")
  public ApiResult<PageResult<Map<String, Object>>> bookmarks(
      @RequestAttribute("authenticatedUsername") String user,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(social.bookmarks(user, pageNo, size));
  }

  @GetMapping("/me/posts")
  public ApiResult<PageResult<Map<String, Object>>> ownPosts(
      @RequestAttribute("authenticatedUsername") String user,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(social.ownPosts(user, pageNo, size));
  }

  @GetMapping("/me/feed")
  public ApiResult<PageResult<com.liushao.model.vo.PostVO>> feed(
      @RequestAttribute("authenticatedUsername") String user,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "") String tag,
      @RequestParam(defaultValue = "") String author) {
    return ApiResult.success(
        PageResult.from(posts.following(users.requireUser(user), pageNo, size, query, tag, author)));
  }

  @GetMapping("/me/posts/{id}")
  public ApiResult<com.liushao.model.vo.PostDetail> ownPost(
      @PathVariable int id, @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(posts.ownDetail(id, users.requireUser(user)));
  }

  @GetMapping("/users/{username}/relationship")
  public ApiResult<Map<String, Object>> relationship(
      @PathVariable String username, @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.relationship(username, user));
  }

  @PostMapping("/users/{username}/follow")
  public ApiResult<Map<String, Object>> follow(
      @PathVariable String username, @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.follow(username, user, true));
  }

  @DeleteMapping("/users/{username}/follow")
  public ApiResult<Map<String, Object>> unfollow(
      @PathVariable String username, @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.follow(username, user, false));
  }

  @GetMapping("/me/following")
  public ApiResult<PageResult<Map<String, Object>>> following(
      @RequestAttribute("authenticatedUsername") String user,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(social.follows(user, false, pageNo, size));
  }

  @GetMapping("/me/followers")
  public ApiResult<PageResult<Map<String, Object>>> followers(
      @RequestAttribute("authenticatedUsername") String user,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(social.follows(user, true, pageNo, size));
  }

  @GetMapping("/notifications")
  public ApiResult<PageResult<Map<String, Object>>> notifications(
      @RequestAttribute("authenticatedUsername") String user,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(social.notifications(user, pageNo, size));
  }

  @GetMapping("/notifications/unread")
  public ApiResult<Map<String, Long>> unreadNotifications(
      @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.unreadNotifications(user));
  }

  @PostMapping("/notifications/{id}/read")
  public ApiResult<Void> readNotification(
      @PathVariable long id, @RequestAttribute("authenticatedUsername") String user) {
    social.readNotification(user, id);
    return ApiResult.success();
  }

  @PostMapping("/notifications/read-all")
  public ApiResult<Void> readNotifications(@RequestAttribute("authenticatedUsername") String user) {
    social.readNotification(user, null);
    return ApiResult.success();
  }

  @GetMapping("/messages/conversations")
  public ApiResult<PageResult<Map<String, Object>>> conversations(
      @RequestAttribute("authenticatedUsername") String user,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(social.conversations(user, pageNo, size));
  }

  @GetMapping("/messages")
  public ApiResult<PageResult<Map<String, Object>>> messages(
      @RequestAttribute("authenticatedUsername") String user,
      @RequestParam String username,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(social.messages(user, username, pageNo, size));
  }

  @PostMapping("/messages")
  public ApiResult<Map<String, Object>> send(
      @RequestAttribute("authenticatedUsername") String user, @Valid @RequestBody Message input) {
    return ApiResult.success(social.sendMessage(user, input.username(), input.content()));
  }

  @PostMapping("/messages/read")
  public ApiResult<Void> readMessages(
      @RequestAttribute("authenticatedUsername") String user,
      @Valid @RequestBody Counterpart input) {
    social.readMessages(user, input.username());
    return ApiResult.success();
  }

  @GetMapping("/messages/unread")
  public ApiResult<Map<String, Long>> unreadMessages(
      @RequestAttribute("authenticatedUsername") String user) {
    return ApiResult.success(social.unreadMessages(user));
  }
}
