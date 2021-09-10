package com.liushao.service;

import com.liushao.dao.BmsBillboardDao;
import com.liushao.model.entity.BmsBillboard;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class BmsBillboardService {

    @Resource
    private BmsBillboardDao bmsBillboardDao;

    /**
     * 查询要显示的公告板内容
     * @return list
     */
    public List<BmsBillboard> findAllBillboardIsShow(){
        return bmsBillboardDao.findAllByShowTrue();
    }
}
