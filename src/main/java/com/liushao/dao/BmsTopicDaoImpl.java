package com.liushao.dao;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import com.liushao.model.vo.PostVO;
import com.liushao.utils.EntityUtils;

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
                "select t.id,t.title,t.user_id,t.comments,t.view,t.collects,t.top,t.essence,t.create_time ,t.modify_time,u.username,u.alias, u.avatar from bms_post t LEFT JOIN ums_user u ON t.user_id = u.id where 1=1");
        StringBuilder countBuilder = new StringBuilder(
                "select count(t.id) from bms_post t LEFT JOIN ums_user u ON t.user_id = u.id where 1=1");
        if ("hot".equals(tab)) {
            dataBuilder.append(
                    " and date(t.create_time) <= date_add(curdate(), interval 1 day) and date(t.create_time) &gt;= date_sub(curdate(), interval 7 day) order by t.view desc, t.create_time desc");
            countBuilder.append(
                    " and date(t.create_time) <= date_add(curdate(), interval 1 day) and date(t.create_time) &gt;= date_sub(curdate(), interval 7 day)");
        } else {
            dataBuilder.append(" order by t.create_time desc");
        }
        String dataSql = dataBuilder.toString();
        String countSql = countBuilder.toString();
        log.info("dataSql===={}", dataSql);
        log.info("countSql===={}", countSql);
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        Query countQuery = entityManager.createNativeQuery(countSql);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());
        BigInteger count = (BigInteger) countQuery.getSingleResult();
        long total = count.longValue();
        List<Object[]> resultList = dataQuery.getResultList();
        List<PostVO> castEntity = EntityUtils.castEntity(resultList, PostVO.class,new PostVO());
        List<PostVO> content = total > pageable.getOffset() ? castEntity
                : Collections.<PostVO>emptyList();
        return new PageImpl<>(content, pageable, total);
    }

}
