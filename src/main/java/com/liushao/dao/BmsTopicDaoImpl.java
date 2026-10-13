package com.liushao.dao;


import java.util.Collections;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import com.liushao.model.vo.PostVO;



import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BmsTopicDaoImpl {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 分页查询带文章数据,关联查询,关联用户表
     * 
     * @param tab      是否热门
     * @param pageable 分页
     * @return Page<PostVO>
     */
    public Page<PostVO> findPostList(String tab, Pageable pageable) {
        StringBuilder dataBuilder = new StringBuilder(
                "select t.id,t.title,t.user_id userId,t.comments,t.view,t.collects,t.top,t.essence,t.create_time createTime,t.modify_time modifyTime,u.username,u.alias,u.avatar from bms_post t LEFT JOIN ums_user u ON t.user_id = u.id where 1=1");
        StringBuilder countBuilder = new StringBuilder(
                "select count(t.id) from bms_post t LEFT JOIN ums_user u ON t.user_id = u.id where 1=1");
        if ("hot".equals(tab)) {
            dataBuilder.append(
                    " and date(t.create_time) <= date_add(curdate(), interval 1 day) and date(t.create_time) >= date_sub(curdate(), interval 7 day) order by t.view desc, t.create_time desc");
            countBuilder.append(
                    " and date(t.create_time) <= date_add(curdate(), interval 1 day) and date(t.create_time) >= date_sub(curdate(), interval 7 day)");
        } else {
            dataBuilder.append(" order by t.create_time desc");
        }
        String dataSql = dataBuilder.toString();
        String countSql = countBuilder.toString();
        log.info("dataSql===={}", dataSql);
        log.info("countSql===={}", countSql);
        var dataQuery = entityManager.unwrap(org.hibernate.Session.class).createNativeQuery(dataSql, Object[].class);
        Query countQuery = entityManager.createNativeQuery(countSql);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());
        Number count = (Number) countQuery.getSingleResult();
        long total = count.longValue();
        List<PostVO> resultList = dataQuery.getResultList().stream().map(row -> {
            PostVO post = new PostVO();
            post.setId(integer(row[0])); post.setTitle((String) row[1]);
            post.setUserId(integer(row[2])); post.setComments(integer(row[3]));
            post.setView(integer(row[4])); post.setCollects(integer(row[5]));
            post.setTop(bool(row[6])); post.setEssence(bool(row[7]));
            post.setCreateTime((java.util.Date) row[8]); post.setModifyTime((java.util.Date) row[9]);
            post.setUsername((String) row[10]); post.setAlias((String) row[11]); post.setAvatar((String) row[12]);
            post.setTags(List.of());
            return post;
        }).toList();
        List<PostVO> content = total > pageable.getOffset() ? resultList
                : Collections.<PostVO>emptyList();
        return new PageImpl<>(content, pageable, total);
    }

    private static Integer integer(Object value) { return value == null ? null : ((Number) value).intValue(); }
    private static Boolean bool(Object value) { return value == null ? null : value instanceof Boolean b ? b : ((Number) value).intValue() != 0; }
}
