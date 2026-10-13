package com.liushao.controller;

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import com.liushao.common.api.ApiResult;
import com.liushao.model.dto.LoginDTO;
import com.liushao.model.dto.RegisterDTO;
import com.liushao.model.entity.UmsUser;
import com.liushao.service.UmsUserService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.util.ObjectUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import static com.liushao.utils.JwtUtil.USER_NAME;

@RestController
@RequestMapping("/ums/user")
@Tag(name = "用户控制层")
public class UmsUserController {

    @Resource
    private UmsUserService umsUserService;

    /**
     * 注册
     * @param registerDTO dto
     * @return UmsUser
     */
    @Operation(summary = "注册")
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
    @Operation(summary = "登录")
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
    
    /**
     * 获取用户信息
     * @param userName 用户名
     * @return UmsUser
     */
    @Operation(summary = "获取用户信息")
    @GetMapping(value = "/info")
    public ApiResult<UmsUser> getUser(@RequestHeader(value = USER_NAME) String userName) {
        UmsUser user = umsUserService.findUserByUsername(userName);
        return ApiResult.success(user);
    }

    /**
     * 注销登录
     * @return Object
     */
    @Operation(summary = "注销登录")
    @GetMapping(value = "/logout")
    public ApiResult<Object> logOut() {
        return ApiResult.success(null, "注销成功");
    }
}
