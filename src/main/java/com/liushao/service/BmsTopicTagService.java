package com.liushao.service;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

import javax.annotation.Resource;

import com.liushao.dao.BmsTopicTagDao;
import com.liushao.model.entity.BmsTag;
import com.liushao.model.entity.BmsTopicTag;

@Slf4j
@Service
public class BmsTopicTagService {

    @Resource
    private BmsTopicTagDao bmsTopicTagDao;

    /**
     * 创建关联数据
     * 
     * @param topicId 帖子Id
     * @param tags    标签
     */
    public void createTopicTag(Integer topicId, List<BmsTag> tags) {
        // 先删除所有topic对应的记录
        int delTopicIdCount = bmsTopicTagDao.deleteByTopicId(topicId);
        log.info("被删除的帖子标签数量为{}", delTopicIdCount);
        // 循环保存
        tags.forEach(tag -> {
            BmsTopicTag bmsTopicTag = new BmsTopicTag();
            bmsTopicTag.setTagId(tag.getId());
            bmsTopicTag.setTopicId(topicId);
            bmsTopicTagDao.save(bmsTopicTag);
        });
    }
}
