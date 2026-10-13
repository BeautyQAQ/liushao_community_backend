package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.entity.BmsPromotion;
import com.liushao.service.BmsPromotionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/promotion")
@Tag(name = "推广链接")
public class BmsPromotionController {

    @Resource
    private BmsPromotionService bmsPromotionService;

    @Operation(summary = "获取全部推广链接")
    @GetMapping("/all")
    public ApiResult<List<BmsPromotion>> list(){
        return ApiResult.success(bmsPromotionService.list());
    }
}
