package com.liushao.controller;

import javax.annotation.Resource;

import com.liushao.common.api.ApiResult;
import com.liushao.model.dto.CreateTopicDTO;
import com.liushao.model.entity.BmsPost;
import com.liushao.model.entity.UmsUser;
import com.liushao.model.vo.PostVO;
import com.liushao.service.BmsPostService;
import com.liushao.service.UmsUserService;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static com.liushao.utils.JwtUtil.USER_NAME;

@RestController
@RequestMapping("/post")
@Api("帖子控制层")
public class BmsPostController {

    @Resource
    private BmsPostService bmsPostService;

    @Resource
    private UmsUserService umsUserService;

    /**
     * 分页查询帖子列表
     * 
     * @param tab      热门/时间
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return page
     */
    @ApiOperation(value = "获取分页帖子列表")
    @GetMapping(value = "/list")
    public ApiResult<Page<PostVO>> list(@RequestParam(value = "tab", defaultValue = "latest") String tab,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "size", defaultValue = "10") Integer pageSize) {
        return ApiResult.success(bmsPostService.getList(tab, pageNo, pageSize));
    }

    /**
     * 创建文章
     * 
     * @param userName 用户名
     * @param dto      文章dto
     * @return 文章BmsPost
     */
    @ApiOperation(value = "创建文章")
    @PostMapping(value = "/add")
    public ApiResult<BmsPost> add(@RequestHeader(value = USER_NAME) String userName, @RequestBody CreateTopicDTO dto) {
        UmsUser user = umsUserService.findUserByUsername(userName);
        BmsPost post = bmsPostService.add(dto, user);
        return ApiResult.success(post);
    }

}
