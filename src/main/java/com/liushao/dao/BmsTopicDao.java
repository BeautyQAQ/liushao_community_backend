package com.liushao.dao;

import com.liushao.model.entity.BmsPost;
import com.liushao.model.vo.PostVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BmsTopicDao
    extends JpaRepository<BmsPost, Integer>, JpaSpecificationExecutor<BmsPost> {

  /**
   * 分页查询带文章数据,关联查询,关联用户表
   *
   * @param tab 是否热门
   * @param pageable 分页
   * @return Page<PostVO>
   */
  Page<PostVO> findPostList(String tab, Pageable pageable);

  /**
   * 根据文章标题查询
   *
   * @param title 标题
   * @return BmsPost
   */
  BmsPost findByTitle(String title);

  Page<PostVO> findPostList(String tab, Pageable pageable, String query, String tag, String author);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select p from BmsPost p where p.id=:id")
  java.util.Optional<BmsPost> findLockedById(
      @org.springframework.data.repository.query.Param("id") Integer id);

  @org.springframework.data.jpa.repository.Modifying(
      clearAutomatically = true,
      flushAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      "update BmsPost p set p.view=p.view+1 where p.id=:id")
  int incrementViews(@org.springframework.data.repository.query.Param("id") Integer id);
}
