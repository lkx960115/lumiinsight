package com.lumiinsight.common.api;

import lombok.Data;

@Data
public class ApiResult<T> {
    private String code;
    private String message;
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.setCode("0");
        r.setMessage("ok");
        r.setData(data);
        return r;
    }

    public static ApiResult<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        ApiResult<T> r = new ApiResult<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
