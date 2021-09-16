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

import javax.annotation.Resource;

@Slf4j
@Service
public class UmsUserService {

    @Resource
    private UmsUserDao umsUserDao;

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
        String token = null;
        try {
            UmsUser umsUser = findUserByUsername(loginDTO.getUsername());
            BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
            String encode = bCryptPasswordEncoder.encode(loginDTO.getPassword());
            if (encode.equals(umsUser.getPassword())) {
                throw new Exception("密码错误");
            }
            token = JwtUtil.generateToken(umsUser.getUsername());
        } catch (Exception e) {
            log.warn("用户不存在or密码验证失败=======>{}", loginDTO.getUsername());
        }
        return token;
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
        umsUserDao.save(umsUser);
        return umsUser;
    }
}