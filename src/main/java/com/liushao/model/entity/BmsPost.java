package com.liushao.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

@NoArgsConstructor
@Entity
@Table(name = "bms_post")
@Schema(description = "帖子")
public class BmsPost implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 主键 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /** 标题 */
  @NotBlank(message = "标题不可以为空")
  @Column(length = 160, nullable = false)
  private String title;

  /** markdown */
  @NotBlank(message = "内容不可以为空")
  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  /** 作者ID */
  private Integer userId;

  /** 评论数 */
  private Integer comments = 0;

  /** 收藏数 */
  private Integer collects = 0;

  private Integer likes = 0;

  @Column(length = 16, nullable = false)
  private String moderationStatus = "PUBLISHED";

  private String moderationReason;

  public Integer getLikes() {
    return likes;
  }

  public void setLikes(Integer value) {
    likes = value;
  }

  public String getModerationStatus() {
    return moderationStatus;
  }

  public void setModerationStatus(String value) {
    moderationStatus = value;
  }

  public String getModerationReason() {
    return moderationReason;
  }

  public void setModerationReason(String value) {
    moderationReason = value;
  }

  /** 浏览数 */
  private Integer view = 0;

  /** 专栏ID，默认不分栏 */
  private Integer sectionId = 0;

  /** 置顶 */
  private Boolean top = false;

  /** 加精 */
  private Boolean essence = false;

  /** 创建时间 */
  @CreatedDate private Date createTime;

  /** 修改时间 */
  @CreatedDate private Date modifyTime;

  @Version private Long version;

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long value) {
    version = value;
  }

  @jakarta.persistence.PrePersist
  void timestamps() {
    if (createTime == null) createTime = new Date();
    if (modifyTime == null) modifyTime = new Date();
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public Integer getComments() {
    return comments;
  }

  public void setComments(Integer comments) {
    this.comments = comments;
  }

  public Integer getCollects() {
    return collects;
  }

  public void setCollects(Integer collects) {
    this.collects = collects;
  }

  public Integer getView() {
    return view;
  }

  public void setView(Integer view) {
    this.view = view;
  }

  public Integer getSectionId() {
    return sectionId;
  }

  public void setSectionId(Integer sectionId) {
    this.sectionId = sectionId;
  }

  public Boolean getTop() {
    return top;
  }

  public void setTop(Boolean top) {
    this.top = top;
  }

  public Boolean getEssence() {
    return essence;
  }

  public void setEssence(Boolean essence) {
    this.essence = essence;
  }

  public Date getCreateTime() {
    return createTime;
  }

  public void setCreateTime(Date createTime) {
    this.createTime = createTime;
  }

  public Date getModifyTime() {
    return modifyTime;
  }

  public void setModifyTime(Date modifyTime) {
    this.modifyTime = modifyTime;
  }
}
