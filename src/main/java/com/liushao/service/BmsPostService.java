package com.liushao.service;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.liushao.dao.BmsTagDao;
import com.liushao.dao.BmsTopicDao;
import com.liushao.dao.BmsTopicTagDao;
import com.liushao.model.entity.BmsTag;
import com.liushao.model.entity.BmsTopicTag;
import com.liushao.model.vo.PostVO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

public class BmsPostService {

    @Resource
    private BmsTopicDao bmsTopicDao;

    @Resource
    private BmsTopicTagDao bmsTopicTagDao;

    @Resource
    private BmsTagDao bmsTagDao;

    /**
     * 分页查询帖子列表
     * 
     * @param tab  是否热门
     * @param page 页码
     * @param size 大小
     * @return page
     */
    public Page<PostVO> getList(String tab, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        Page<PostVO> pagePostList = bmsTopicDao.findPostList(tab, pageRequest);
        pagePostList.forEach(topic -> {
            List<BmsTopicTag> topicTagList = bmsTopicTagDao.findByTopicId(topic.getId());
            if (!topicTagList.isEmpty()) {
                List<Integer> tagIds = topicTagList.stream().map(BmsTopicTag::getTagId).collect(Collectors.toList());
                List<BmsTag> BmsTagList = bmsTagDao.findAllById(tagIds);
                topic.setTags(BmsTagList);
            }
        });
        return pagePostList;
    }
}
