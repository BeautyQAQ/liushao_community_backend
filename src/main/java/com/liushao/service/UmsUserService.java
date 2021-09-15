package com.liushao.service;

import com.liushao.common.exception.ApiAsserts;
import com.liushao.dao.UmsUserDao;
import com.liushao.model.dto.RegisterDTO;
import com.liushao.model.entity.UmsUser;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class UmsUserService {

    @Resource
    private UmsUserDao umsUserDao;

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