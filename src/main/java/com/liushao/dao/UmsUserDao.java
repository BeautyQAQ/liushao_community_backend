package com.liushao.dao;

import com.liushao.model.entity.UmsUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UmsUserDao
    extends JpaRepository<UmsUser, Integer>, JpaSpecificationExecutor<UmsUser> {

  /**
   * 判断用户名是否存在
   *
   * @param username 用户名
   * @return 结果数量
   */
  boolean existsByEmail(String email);

  int countByUsername(String username);

  /**
   * 获取用户信息
   *
   * @param username 用户名
   * @return 用户信息
   */
  UmsUser findByUsername(String username);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select user from UmsUser user where user.username = :username")
  UmsUser findLockedByUsername(@Param("username") String username);
}
