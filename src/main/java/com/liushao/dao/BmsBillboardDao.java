package com.liushao.dao;

import com.liushao.model.entity.BmsBillboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface BmsBillboardDao extends JpaRepository<BmsBillboard,Integer>, JpaSpecificationExecutor<BmsBillboard> {
    List<BmsBillboard> findAllByShowTrue();
}
