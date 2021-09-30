package com.liushao.dao;

import com.liushao.model.entity.BmsTag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BmsTagDao extends JpaRepository<BmsTag,Integer>, JpaSpecificationExecutor<BmsTag> {
    /**
     * 根据标签名查询标签
     * @param name 标签名
     * @return 标签 BmsTag
     */
    BmsTag findByName(String name);
}
