package com.liushao.dao;

import com.liushao.model.entity.BmsTag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BmsTagDao extends JpaRepository<BmsTag,Integer>, JpaSpecificationExecutor<BmsTag> {
    
}
