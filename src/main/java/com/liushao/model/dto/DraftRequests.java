package com.liushao.model.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class DraftRequests {
  private DraftRequests() {}

  public record Save(
      @NotNull @Size(max = 160) String title,
      @NotNull @Size(max = 20000) String content,
      @NotNull @Size(max = 5) List<@NotBlank @Size(max = 32) String> tags,
      @Positive Integer postId,
      @Min(0) Long postVersion,
      @NotNull @Min(0) Long version) {}

  public record Revision(@NotNull @Min(0) Long version) {}
}
