package com.liushao.model.entity;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.persistence.*;

import org.springframework.data.annotation.CreatedDate;

import java.io.Serializable;
import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name="bms_billboard")
@ApiModel(value = "公告板")
public class BmsBillboard implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 公告牌
     */
    private String content;

    /**
     * 公告时间
     */
    @CreatedDate
    private Date createTime;

    /**
     * 1：展示中，0：过期, 默认设置为0过期
     */
    private boolean show = false;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public boolean isShow() {
        return show;
    }

    public void setShow(boolean show) {
        this.show = show;
    }
}
