package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.entity.BmsTip;
import com.liushao.service.BmsTipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/tip")
@Tag(name = "每日一句")
public class BmsTipController {

    @Resource
    private BmsTipService bmsTipService;

    @Operation(summary = "随机获取每日一句")
    @GetMapping("/today")
    public ApiResult<BmsTip> findRandomTip() {
        return ApiResult.success(bmsTipService.findRandomTip());
    }
}
