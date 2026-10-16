package com.lumiinsight.common.exception;

import com.lumiinsight.common.api.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> biz(BizException e) {
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ApiResult<Void> valid(Exception e) {
        String msg = "参数不合法";
        if (e instanceof MethodArgumentNotValidException v && v.getBindingResult().getFieldError() != null) {
            msg = v.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof BindException b && b.getBindingResult().getFieldError() != null) {
            msg = b.getBindingResult().getFieldError().getDefaultMessage();
        }
        return ApiResult.fail("VALIDATION_ERROR", msg);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ApiResult<Void> badCred() {
        return ApiResult.fail("AUTH_FAILED", "用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResult<Void> denied() {
        return ApiResult.fail("FORBIDDEN", "没有权限");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResult<Void> tooLarge() {
        return ApiResult.fail("FILE_TOO_LARGE", "文件超过 50MB 限制");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> other(Exception e, HttpServletRequest req) {
        log.error("未处理异常 {} {}", req.getMethod(), req.getRequestURI(), e);
        return ApiResult.fail("INTERNAL_ERROR", "服务暂时不可用");
    }
}
