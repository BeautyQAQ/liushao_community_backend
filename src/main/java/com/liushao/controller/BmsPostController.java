package com.liushao.controller;

import javax.annotation.Resource;

import com.liushao.model.vo.PostVO;
import com.liushao.service.BmsPostService;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/post")
@Api("帖子控制层")
public class BmsPostController {

    @Resource
    private BmsPostService bmsPostService;

    /**
     * 分页查询帖子列表
     * @param tab 热门/时间
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return page
     */
    @ApiOperation(value = "获取分页帖子列表")
    @GetMapping(value="/list")
    public Page<PostVO> list(@RequestParam(value = "tab", defaultValue = "latest") String tab,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "size", defaultValue = "10") Integer pageSize) {
        return bmsPostService.getList(tab, pageNo, pageSize);
    }
}
