package com.liushao.service;

import com.liushao.common.exception.ApiAsserts;
import com.liushao.dao.UmsUserDao;
import com.liushao.model.dto.LoginDTO;
import com.liushao.model.dto.RegisterDTO;
import com.liushao.model.entity.UmsUser;
import com.liushao.utils.JwtUtil;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;

@Slf4j
@Service
public class UmsUserService {

    @Resource
    private UmsUserDao umsUserDao;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 获取用户信息
     * @param username 用户名
     * @return 用户信息
     */
    public UmsUser findUserByUsername(String username){
        return umsUserDao.findByUsername(username);
    }

    /**
     * 用户登录
     * @param loginDTO dto
     * @return token
     */
    public String executeLogin(LoginDTO loginDTO){
        UmsUser user = findUserByUsername(loginDTO.getUsername());
        if (user == null || !Boolean.TRUE.equals(user.getStatus()) ||
                !new BCryptPasswordEncoder().matches(loginDTO.getPassword(), user.getPassword())) {
            return null;
        }
        return jwtUtil.generateToken(user.getUsername());
    }

    /**
     * 用户注册
     * @return BmsTip
     */
    @Transactional
    public UmsUser executeRegister(RegisterDTO registerDTO){
        int countByUsername = umsUserDao.countByUsername(registerDTO.getName());
        if(countByUsername>0){
            ApiAsserts.fail("账户已经存在!");
        }
        UmsUser umsUser = new UmsUser();
        umsUser.setUsername(registerDTO.getName());
        umsUser.setAlias(registerDTO.getName());
        umsUser.setEmail(registerDTO.getEmail());
        umsUser.setStatus(true);
        // 密码加密
        BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
        umsUser.setPassword(bCryptPasswordEncoder.encode(registerDTO.getPass()));
        umsUser.setCreateTime(new java.util.Date());
        umsUser.setModifyTime(new java.util.Date());
        umsUserDao.save(umsUser);
        return umsUser;
    }
}