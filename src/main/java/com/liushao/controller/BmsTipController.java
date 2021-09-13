package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.entity.BmsTip;
import com.liushao.service.BmsTipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/tip")
public class BmsTipController {

    @Resource
    private BmsTipService bmsTipService;

    @GetMapping("/today")
    public ApiResult<BmsTip> findRandomTip() {
        return ApiResult.success(bmsTipService.findRandomTip());
    }
}
