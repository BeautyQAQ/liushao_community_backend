package com.liushao.dao;

import com.liushao.model.entity.UmsUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UmsUserDao extends JpaRepository<UmsUser,Integer>, JpaSpecificationExecutor<UmsUser> {
    int countByUsername(String username);
}
