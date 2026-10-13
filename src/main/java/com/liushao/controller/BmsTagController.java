package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.entity.BmsTag;
import com.liushao.service.BmsTagService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tag")
public class BmsTagController {
  private final BmsTagService tags;

  public BmsTagController(BmsTagService tags) {
    this.tags = tags;
  }

  @GetMapping("/list")
  public ApiResult<List<BmsTag>> list() {
    return ApiResult.success(tags.popular());
  }
}
