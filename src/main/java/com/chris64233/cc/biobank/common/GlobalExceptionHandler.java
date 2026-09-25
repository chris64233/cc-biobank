package com.chris64233.cc.biobank.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        ApiError body = ApiError.of(ex.code(), ex.getMessage(), ex.details());
        return ResponseEntity.status(ex.code().status()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ApiError body = ApiError.of(ErrorCode.VALIDATION_FAILED, "请求参数校验失败", details);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        ApiError body = ApiError.of(ErrorCode.INVALID_REQUEST, "请求体无法解析", Map.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        ApiError body = ApiError.of(ErrorCode.NOT_FOUND, "资源不存在", Map.of());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        ApiError body = ApiError.of(ErrorCode.INTERNAL_ERROR, "服务器内部错误", Map.of());
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(body);
    }
}
