package com.liushao.dao;

import com.liushao.model.entity.BmsTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BmsTagDao
    extends JpaRepository<BmsTag, Integer>, JpaSpecificationExecutor<BmsTag> {
  /**
   * 根据标签名查询标签
   *
   * @param name 标签名
   * @return 标签 BmsTag
   */
  BmsTag findByName(String name);

  java.util.List<BmsTag> findTop50ByTopicCountGreaterThanOrderByTopicCountDescNameAsc(
      Integer count);

  @org.springframework.data.jpa.repository.Modifying(
      flushAutomatically = true,
      clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      "update BmsTag t set t.topicCount=t.topicCount+:delta where t.id=:id")
  int adjustCount(
      @org.springframework.data.repository.query.Param("id") Integer id,
      @org.springframework.data.repository.query.Param("delta") int delta);
}
