package com.liushao.model.vo;

import com.liushao.model.entity.BmsTag;
import java.util.*;

public record PostDetail(
    Integer id,
    Integer userId,
    String username,
    String alias,
    String avatar,
    String title,
    String content,
    Integer view,
    Long version,
    Date createTime,
    Date modifyTime,
    List<BmsTag> tags,
    Integer likes,
    Integer comments,
    Integer collects,
    Boolean top,
    Boolean essence,
    String status,
    String moderationReason) {}
