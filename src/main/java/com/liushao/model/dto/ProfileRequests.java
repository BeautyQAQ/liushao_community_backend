package com.liushao.model.dto;

import jakarta.validation.constraints.*;

public final class ProfileRequests {
  public record Profile(@NotBlank @Size(max = 64) String alias, @Size(max = 255) String bio) {}

  public record Password(
      @NotBlank String currentPass,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank String checkPass) {}
}
