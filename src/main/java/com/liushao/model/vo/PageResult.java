package com.liushao.model.vo;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable API pagination independent of Spring Data serialization internals. */
public record PageResult<T>(
    List<T> content, long totalElements, int totalPages, int number, int size) {
  public static <T> PageResult<T> from(Page<T> page) {
    return new PageResult<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }
}
