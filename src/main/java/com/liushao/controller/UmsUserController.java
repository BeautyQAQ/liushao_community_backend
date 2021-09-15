package com.liushao.controller;

import javax.annotation.Resource;
import javax.validation.Valid;

import com.liushao.common.api.ApiResult;
import com.liushao.model.dto.RegisterDTO;
import com.liushao.model.entity.UmsUser;
import com.liushao.service.UmsUserService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.util.ObjectUtil;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/ums/user")
public class UmsUserController {

    @Resource
    private UmsUserService umsUserService;

    /**
     * 注册
     * @param registerDTO dto
     * @return UmsUser
     */
    @PostMapping(value="register")
    public ApiResult<UmsUser> postMethodName(@Valid @RequestBody RegisterDTO registerDTO) {
        UmsUser executeRegister = umsUserService.executeRegister(registerDTO);        
        if(ObjectUtil.isEmpty(executeRegister)){
            return ApiResult.failed("账号注册失败");
        }
        return ApiResult.success(executeRegister);
    }
    
}
