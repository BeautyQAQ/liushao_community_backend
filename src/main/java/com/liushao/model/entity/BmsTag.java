package com.liushao.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Entity
@Table(name = "bms_tag")
@Schema(description = "标签")
public class BmsTag implements Serializable {

  private static final long serialVersionUID = 3257790983905872243L;

  /** 主键 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(length = 32, nullable = false, unique = true)
  private String name;

  /** 当前标签下的话题个数 */
  private Integer topicCount = 0;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getTopicCount() {
    return topicCount;
  }

  public void setTopicCount(Integer topicCount) {
    this.topicCount = topicCount;
  }
}
