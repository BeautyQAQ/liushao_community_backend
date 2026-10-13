package com.liushao.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginDTO {
  @NotBlank
  @Size(max = 32)
  private String username;

  @NotBlank
  @Size(max = 72)
  private String password;

  private Boolean rememberMe;
}
