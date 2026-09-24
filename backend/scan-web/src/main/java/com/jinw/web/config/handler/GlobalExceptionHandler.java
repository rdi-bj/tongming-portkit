package com.jinw.web.config.handler;

import com.jinw.web.base.RestResult;
import com.jinw.web.config.exception.BusinessException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.StringJoiner;

@Hidden
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RestResult<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return RestResult.error(500, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RestResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        StringJoiner sj = new StringJoiner(", ");
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            sj.add(error.getField() + ": " + error.getDefaultMessage());
        }
        log.warn("参数校验异常: {}", sj);
        return RestResult.error(500, sj.toString());
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RestResult<Void> handleBindException(BindException e) {
        StringJoiner sj = new StringJoiner(", ");
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            sj.add(error.getField() + ": " + error.getDefaultMessage());
        }
        log.warn("参数绑定异常: {}", sj);
        return RestResult.error(500, sj.toString());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RestResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("约束校验异常: {}", e.getMessage());
        return RestResult.error(500, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RestResult<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return RestResult.error(500, "上传文件大小超过限制，最大支持 1GB");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public RestResult<Void> handleException(NoResourceFoundException e) {
        log.error("资源未找到: ", e);
        return RestResult.error(404, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RestResult<Void> handleException(IllegalStateException e) {
        log.error("系统异常: ", e);
        return RestResult.error(500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RestResult<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return RestResult.error(500, "系统异常，请稍后重试");
    }
}