package com.liushao.service;

import com.liushao.dao.BmsTipDao;
import com.liushao.model.entity.BmsTip;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class BmsTipService {

    @Resource
    private BmsTipDao bmsTipDao;

    /**
     * 随机查询一条Tip
     * @return BmsTip
     */
    public BmsTip findRandomTip(){
        return bmsTipDao.findRandomTip();
    }
}
