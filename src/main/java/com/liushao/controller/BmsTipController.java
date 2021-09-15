package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.entity.BmsTip;
import com.liushao.service.BmsTipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.annotation.Resource;

@RestController
@RequestMapping("/tip")
@Api(tags = "每日一句")
public class BmsTipController {

    @Resource
    private BmsTipService bmsTipService;

    @ApiOperation(value = "随机获取每日一句")
    @GetMapping("/today")
    public ApiResult<BmsTip> findRandomTip() {
        return ApiResult.success(bmsTipService.findRandomTip());
    }
}
