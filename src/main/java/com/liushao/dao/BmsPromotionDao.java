package com.liushao.dao;

import com.liushao.model.entity.BmsPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BmsPromotionDao extends JpaRepository<BmsPromotion,Integer>, JpaSpecificationExecutor<BmsPromotion> {
}
