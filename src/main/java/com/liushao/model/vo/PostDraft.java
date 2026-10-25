package com.liushao.model.vo;

import java.util.List;

public record PostDraft(
    String id,
    Integer postId,
    Long postVersion,
    String title,
    String content,
    List<String> tags,
    long version,
    String updatedAt) {}
