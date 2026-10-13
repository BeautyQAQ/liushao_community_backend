package com.liushao.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterDTO {
  @NotBlank
  @Pattern(regexp = "[a-zA-Z0-9_]{2,32}", message = "账号为 2 至 32 位字母、数字或下划线")
  private String name;

  @NotBlank
  @Size(min = 8, max = 72, message = "密码至少 8 个字符，最多 72 字节")
  private String pass;

  @NotBlank private String checkPass;

  @NotBlank
  @Email
  @Size(max = 128)
  private String email;
}
