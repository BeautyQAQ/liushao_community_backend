package com.liushao.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Date;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

@NoArgsConstructor
@Entity
@Table(name = "ums_user")
@Schema(description = "用户信息")
public class UmsUser implements Serializable {
  private static final long serialVersionUID = -5051120337175047163L;

  /** 主键 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(length = 32, nullable = false, unique = true)
  private String username;

  @Column(length = 64, nullable = false)
  private String alias;

  @JsonIgnore()
  @Column(length = 100, nullable = false)
  private String password;

  private String avatar = "";

  @Column(length = 128, nullable = false, unique = true)
  private String email;

  @Column(length = 32)
  private String mobile;

  private String bio = "";

  private Integer score = 0;

  @JsonIgnore private String token;

  private Boolean active = false;

  /** 状态。1:使用，0:已停用 */
  private Boolean status = true;

  /** 用户角色 */
  private Integer roleId;

  @CreatedDate private Date createTime;

  @CreatedDate private Date modifyTime;

  @JsonIgnore private Long authVersion = 0L;

  public Long getAuthVersion() {
    return authVersion;
  }

  public void setAuthVersion(Long value) {
    authVersion = value;
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

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getAvatar() {
    return avatar;
  }

  public void setAvatar(String avatar) {
    this.avatar = avatar;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getMobile() {
    return mobile;
  }

  public void setMobile(String mobile) {
    this.mobile = mobile;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public Integer getScore() {
    return score;
  }

  public void setScore(Integer score) {
    this.score = score;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public Boolean getStatus() {
    return status;
  }

  public void setStatus(Boolean status) {
    this.status = status;
  }

  public Integer getRoleId() {
    return roleId;
  }

  public void setRoleId(Integer roleId) {
    this.roleId = roleId;
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
