package com.liushao.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.liushao.model.entity.BmsPost;

public interface BmsPostDao extends JpaRepository<BmsPost, Integer>, JpaSpecificationExecutor<BmsPost> {
    /**
     * 根据文章标题查询
     * 
     * @param title 标题
     * @return List<PostVO>
     */
    BmsPost findByTitle(String title);
}
