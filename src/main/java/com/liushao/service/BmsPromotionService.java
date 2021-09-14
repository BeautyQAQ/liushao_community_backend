package com.liushao.service;

import com.liushao.dao.BmsPromotionDao;
import com.liushao.model.entity.BmsPromotion;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class BmsPromotionService {

    @Resource
    private BmsPromotionDao bmsPromotionDao;

    public List<BmsPromotion> list(){
        return bmsPromotionDao.findAll();
    }
}
