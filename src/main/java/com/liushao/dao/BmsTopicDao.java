package com.liushao.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.liushao.model.vo.PostVO;

public interface BmsTopicDao extends JpaRepository<PostVO,Integer>, JpaSpecificationExecutor<PostVO> {

    /**
     * 分页查询带文章数据,关联查询,关联用户表
     * @param tab 是否热门
     * @param pageable 分页
     * @return Page<PostVO>
     */
    Page<PostVO> findPostList(String tab, Pageable pageable);
}
