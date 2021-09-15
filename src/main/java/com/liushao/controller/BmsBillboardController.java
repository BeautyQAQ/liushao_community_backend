package com.liushao.controller;

import com.liushao.common.api.ApiResult;
import com.liushao.model.entity.BmsBillboard;
import com.liushao.service.BmsBillboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/billboard")
@Api(tags = "公告板")
public class BmsBillboardController {

    @Resource
    private BmsBillboardService bmsBillboardService;

    @ApiOperation(value = "展示公告")
    @GetMapping("/show")
    public ApiResult<BmsBillboard> getNotices() {
        List<BmsBillboard> list = bmsBillboardService.findAllBillboardIsShow();
        return ApiResult.success(list.get(list.size() - 1));
    }
}
