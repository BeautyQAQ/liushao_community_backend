package com.liushao.service;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.liushao.dao.BmsTagDao;
import com.liushao.dao.BmsTopicDao;
import com.liushao.dao.BmsTopicTagDao;
import com.liushao.dao.UmsUserDao;
import com.liushao.model.dto.CreateTopicDTO;
import com.liushao.model.entity.BmsPost;
import com.liushao.model.entity.BmsTag;
import com.liushao.model.entity.BmsTopicTag;
import com.liushao.model.entity.UmsUser;
import com.liushao.model.vo.PostVO;
import com.vdurmont.emoji.EmojiParser;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;

@Service
public class BmsPostService {

    @Resource
    private BmsTopicDao bmsTopicDao;

    @Resource
    private BmsTopicTagDao bmsTopicTagDao;

    @Resource
    private BmsTagDao bmsTagDao;

    @Resource
    private UmsUserDao umsUserDao;

    @Resource
    private BmsTopicTagService bmsTopicTagService;

    @Resource
    @Lazy
    private BmsTagService bmsTagService;

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

    /**
     * 添加文章
     * 
     * @param dto  文章dto
     * @param user 用户
     * @return 文章
     */
    @Transactional
    public BmsPost add(CreateTopicDTO dto, UmsUser user) {
        BmsPost post = bmsTopicDao.findByTitle(dto.getTitle());
        Assert.isNull(post, "帖子标题已存在, 请修改!");
        // 保存文章
        BmsPost bmsPost = new BmsPost();
        bmsPost.setUserId(user.getId());
        bmsPost.setTitle(dto.getTitle());
        bmsPost.setContent(EmojiParser.parseToAliases(dto.getContent()));
        bmsTopicDao.save(bmsPost);
        // 用户积分增加
        user.setScore(user.getScore() + 1);
        umsUserDao.save(user);
        // 标签
        if (ObjectUtil.isNotEmpty(dto.getTags())) {
            // 保存标签
            List<BmsTag> tags = bmsTagService.addTags(dto.getTags());
            // 处理标签和帖子的关系
            bmsTopicTagService.createTopicTag(bmsPost.getId(), tags);
        }
        return bmsPost;
    }
}
