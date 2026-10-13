package com.liushao.model.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Data;

@Data
public class CreateTopicDTO {
  @NotBlank
  @Size(max = 160, message = "标题最多 160 个字符")
  private String title;

  @NotBlank
  @Size(max = 20000, message = "正文最多 20000 个字符")
  private String content;

  @NotNull
  @Size(max = 5, message = "最多添加 5 个标签")
  private List<@NotBlank @Size(max = 32) String> tags = List.of();

  @Min(0)
  private Long version;
}
