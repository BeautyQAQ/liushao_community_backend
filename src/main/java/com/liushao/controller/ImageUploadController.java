package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.common.exception.ApiException;
import com.liushao.model.entity.UmsUser;
import com.liushao.security.CommunitySessions;
import com.liushao.service.ImageUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImageUploadController {
  public record Avatar(@NotBlank @Size(max=100) String url) {}
  private final ImageUploadService service;
  private final CommunitySessions sessions;
  public ImageUploadController(ImageUploadService service,CommunitySessions sessions) { this.service=service; this.sessions=sessions; }

  @PostMapping(value="/uploads/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<Map<String,Object>> upload(@RequestAttribute("authenticatedUsername") String user,
      @RequestPart("file") MultipartFile file) {
    if (!sessions.allow("upload:"+user,10)) throw new ApiException(429,"上传过于频繁，请稍后重试");
    return ApiResult.success(service.upload(user,file));
  }
  @GetMapping("/uploads/{name}")
  public ResponseEntity<Resource> image(@PathVariable String name) {
    return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
        .header("X-Content-Type-Options","nosniff")
        .header("Cache-Control","public,max-age=31536000,immutable")
        .body(service.image(name));
  }
  @PostMapping("/ums/user/avatar")
  public ApiResult<UmsUser> avatar(@RequestAttribute("authenticatedUsername") String user,@Valid @RequestBody Avatar input) {
    return ApiResult.success(service.avatar(user,input.url()));
  }
}
