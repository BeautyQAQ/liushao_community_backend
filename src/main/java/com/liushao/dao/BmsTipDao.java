package com.liushao.dao;

import com.liushao.model.entity.BmsTip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface BmsTipDao extends JpaRepository<BmsTip,Integer>, JpaSpecificationExecutor<BmsTip> {

    @Query(value = "select t.* from bms_tip t order by rand() limit 1", nativeQuery = true)
    BmsTip findRandomTip();
}
