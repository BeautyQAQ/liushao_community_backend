package com.liushao.controller;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.validation.Valid;

import com.liushao.common.api.ApiResult;
import com.liushao.model.dto.LoginDTO;
import com.liushao.model.dto.RegisterDTO;
import com.liushao.model.entity.UmsUser;
import com.liushao.service.UmsUserService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.util.ObjectUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/ums/user")
@Api(tags = "用户控制层")
public class UmsUserController {

    @Resource
    private UmsUserService umsUserService;

    /**
     * 注册
     * @param registerDTO dto
     * @return UmsUser
     */
    @ApiOperation(value = "注册")
    @PostMapping(value="register")
    public ApiResult<UmsUser> postMethodName(@Valid @RequestBody RegisterDTO registerDTO) {
        UmsUser executeRegister = umsUserService.executeRegister(registerDTO);        
        if(ObjectUtil.isEmpty(executeRegister)){
            return ApiResult.failed("账号注册失败");
        }
        return ApiResult.success(executeRegister);
    }
    
    /**
     * 登录
     * @param loginDTO dto
     * @return map
     */
    @ApiOperation(value = "登录")
    @PostMapping(value="/login")
    public ApiResult<Map<String, String>> postMethodName(@Valid @RequestBody LoginDTO loginDTO) {
        String token = umsUserService.executeLogin(loginDTO);
        if (ObjectUtil.isEmpty(token)) {
            return ApiResult.failed("账号密码错误");
        }
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put("token", token);
        return ApiResult.success(hashMap, "登录成功");
    }
    
}
