package com.liushao.service;

import com.liushao.dao.BmsTagDao;
import com.liushao.model.entity.BmsTag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

@Service
public class BmsTagService {
    
    @Resource
    private BmsTagDao bmsTagDao;

    /**
     * 添加标签,如果标签存在,则使用数量TopicCount+1
     * @param tagNames 标签名
     * @return List<BmsTag>
     */
    public List<BmsTag> addTags(List<String> tagNames){
        List<BmsTag> tagList = new ArrayList<>();
        for (String name : tagNames) {
            BmsTag bmsTag = bmsTagDao.findByName(name);
            if (bmsTag==null) {
                bmsTag = new BmsTag();
                bmsTag.setName(name);
            } else {
                bmsTag.setTopicCount(bmsTag.getTopicCount()+1);
            }
            bmsTagDao.save(bmsTag);
            tagList.add(bmsTag);
        }
        return tagList;
    }
}
