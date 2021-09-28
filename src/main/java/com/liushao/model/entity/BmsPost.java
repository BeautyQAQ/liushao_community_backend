package com.liushao.model.entity;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import javax.validation.constraints.NotBlank;

import io.swagger.annotations.ApiModel;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Entity
@Table(name="bms_post")
@ApiModel(value = "帖子")
public class BmsPost implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

/**
     * 标题
     */
    @NotBlank(message = "标题不可以为空")
    private String title;
    /**
     * markdown
     */
    @NotBlank(message = "内容不可以为空")
    private String content;

    /**
     * 作者ID
     */
    private String userId;

    /**
     * 评论数
     */
    private Integer comments = 0;

    /**
     * 收藏数
     */
    private Integer collects = 0;

    /**
     * 浏览数
     */
    private Integer view = 0;

    /**
     * 专栏ID，默认不分栏
     */
    private Integer sectionId = 0;

    /**
     * 置顶
     */
    private Boolean top = false;

    /**
     * 加精
     */
    private Boolean essence = false;

    /**
     * 创建时间
     */
    private Date createTime = new Date();

    /**
     * 修改时间
     */
    private Date modifyTime = new Date();

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
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
