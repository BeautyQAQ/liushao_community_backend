package com.liushao.dao;

import java.util.List;

import com.liushao.model.entity.BmsTopicTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BmsTopicTagDao extends JpaRepository<BmsTopicTag, Integer>, JpaSpecificationExecutor<BmsTopicTag> {
    /**
     * 根据文章id查询中间表数据
     * @param topicId 文章id
     * @return List<BmsTopicTag>
     */
    List<BmsTopicTag> findByTopicId(Integer topicId);

    /**
     * 删除这个文章对应的标签id
     * @param topicId 文章Id
     * @return int
     */
    int deleteByTopicId(Integer topicId);
}
