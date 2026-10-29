package com.liushao.controller;

import static com.liushao.service.CommunityOperationsService.*;

import com.liushao.common.api.ApiResult;
import com.liushao.model.vo.PageResult;
import com.liushao.service.CommunityOperationsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/admin")
public class CommunityOperationsController {
  public record Moderation(
      @NotNull @Min(0) Long version,
      @NotBlank @Pattern(regexp = "PUBLISHED|PENDING|HIDDEN") String status,
      @NotNull Boolean top, @NotNull Boolean essence,
      @NotBlank @Size(max = 255) String reason) {}

  private final CommunityOperationsService ops;

  public CommunityOperationsController(CommunityOperationsService ops) {
    this.ops = ops;
  }

  @GetMapping("/users")
  public ApiResult<PageResult<Map<String, Object>>> users(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(ops.users(query, pageNo, size));
  }

  @GetMapping("/users/{id}")
  public ApiResult<Map<String, Object>> user(@PathVariable int id) {
    return ApiResult.success(ops.user(id));
  }

  @PutMapping("/users/{id}/status")
  public ApiResult<Map<String, Object>> userStatus(
      @PathVariable int id,
      @RequestBody Map<String, Object> input,
      @RequestAttribute("internalAdminActor") String actor) {
    return ApiResult.success(
        ops.userStatus(id, bool(input, "status"), text(input, "reason", 255, true), actor));
  }

  @GetMapping("/posts")
  public ApiResult<PageResult<Map<String, Object>>> posts(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(ops.posts(query, status, pageNo, size));
  }

  @GetMapping("/posts/{id}")
  public ApiResult<Map<String, Object>> post(@PathVariable int id) {
    return ApiResult.success(ops.post(id));
  }

  @PutMapping("/posts/{id}/moderation")
  public ApiResult<Map<String, Object>> moderate(
      @PathVariable int id,
      @Valid @RequestBody Moderation input,
      @RequestAttribute("internalAdminActor") String actor) {
    return ApiResult.success(
        ops.moderate(
            id,
            input.version(),
            input.status(),
            input.top(),
            input.essence(),
            input.reason().trim(),
            actor));
  }

  @DeleteMapping("/posts/{id}")
  public ApiResult<Void> deletePost(
      @PathVariable int id,
      @RequestParam String reason,
      @RequestAttribute("internalAdminActor") String actor) {
    ops.deletePost(id, text(Map.of("reason", reason), "reason", 255, true), actor);
    return ApiResult.success();
  }

  @GetMapping("/comments")
  public ApiResult<PageResult<Map<String, Object>>> comments(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(required = false) Integer postId,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(ops.comments(query, postId, pageNo, size));
  }

  @GetMapping("/comments/{id}")
  public ApiResult<Map<String, Object>> comment(@PathVariable long id) {
    return ApiResult.success(ops.comment(id));
  }

  @PutMapping("/comments/{id}/visibility")
  public ApiResult<Map<String, Object>> visibility(
      @PathVariable long id,
      @RequestBody Map<String, Object> input,
      @RequestAttribute("internalAdminActor") String actor) {
    return ApiResult.success(
        ops.commentVisibility(id, bool(input, "visible"), text(input, "reason", 255, true), actor));
  }

  @DeleteMapping("/comments/{id}")
  public ApiResult<Void> deleteComment(
      @PathVariable long id,
      @RequestParam String reason,
      @RequestAttribute("internalAdminActor") String actor) {
    ops.deleteComment(id, text(Map.of("reason", reason), "reason", 255, true), actor);
    return ApiResult.success();
  }

  @GetMapping("/audit")
  public ApiResult<PageResult<Map<String, Object>>> audit(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(ops.auditList(query, pageNo, size));
  }

  @GetMapping("/{kind:billboards|tips|promotions|tags}")
  public ApiResult<PageResult<Map<String, Object>>> catalog(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.success(ops.catalogList(kind, query, pageNo, size));
  }

  @GetMapping("/{kind:billboards|tips|promotions|tags}/{id}")
  public ApiResult<Map<String, Object>> item(@PathVariable String kind, @PathVariable long id) {
    return ApiResult.success(ops.catalogItem(kind, id));
  }

  @PostMapping("/{kind:billboards|tips|promotions|tags}")
  public ApiResult<Map<String, Object>> create(
      @PathVariable String kind,
      @RequestBody Map<String, Object> input,
      @RequestAttribute("internalAdminActor") String actor) {
    return ApiResult.success(ops.saveCatalog(kind, null, input, actor));
  }

  @PutMapping("/{kind:billboards|tips|promotions|tags}/{id}")
  public ApiResult<Map<String, Object>> update(
      @PathVariable String kind,
      @PathVariable long id,
      @RequestBody Map<String, Object> input,
      @RequestAttribute("internalAdminActor") String actor) {
    return ApiResult.success(ops.saveCatalog(kind, id, input, actor));
  }

  @DeleteMapping("/{kind:billboards|tips|promotions|tags}/{id}")
  public ApiResult<Void> delete(
      @PathVariable String kind,
      @PathVariable long id,
      @RequestAttribute("internalAdminActor") String actor) {
    ops.deleteCatalog(kind, id, actor);
    return ApiResult.success();
  }
}
