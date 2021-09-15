package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.entity.BmsPromotion;
import com.liushao.service.BmsPromotionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/promotion")
@Api(tags = "推广链接")
public class BmsPromotionController {

    @Resource
    private BmsPromotionService bmsPromotionService;

    @ApiOperation(value = "获取全部推广链接")
    @GetMapping("/all")
    public ApiResult<List<BmsPromotion>> list(){
        return ApiResult.success(bmsPromotionService.list());
    }
}
