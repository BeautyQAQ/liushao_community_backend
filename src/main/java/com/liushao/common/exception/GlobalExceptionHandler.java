package com.liushao.common.exception;

import com.liushao.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiResult<Void>> api(ApiException e) {
    return error(e.getStatus(), e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResult<Void>> invalid(MethodArgumentNotValidException e) {
    return error(400, e.getBindingResult().getAllErrors().getFirst().getDefaultMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiResult<Void>> invalidInput() {
    return error(400, "请求参数不正确");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiResult<Void>> conflict() {
    return error(409, "账号、邮箱或标签已存在，请检查后重试");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ApiResult<Void>> changed() {
    return error(409, "内容已被更新，请刷新后重试");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResult<Void>> unexpected(Exception e) {
    log.error("Request failed", e);
    return error(500, "服务暂时不可用，请稍后重试");
  }

  private ResponseEntity<ApiResult<Void>> error(int code, String message) {
    return ResponseEntity.status(code).body(new ApiResult<>(code, message, null));
  }
}
