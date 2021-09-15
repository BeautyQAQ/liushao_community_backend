package com.liushao.dao;

import com.liushao.model.entity.UmsUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UmsUserDao extends JpaRepository<UmsUser,Integer>, JpaSpecificationExecutor<UmsUser> {
    
    /**
     * 判断用户名是否存在
     * @param username 用户名
     * @return 结果数量
     */
    int countByUsername(String username);
    
    /**
     * 获取用户信息
     * @param username 用户名
     * @return 用户信息
     */
    UmsUser findByUsername(String username);
}
