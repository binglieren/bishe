package com.example.kaoyan.util;

import lombok.Data;

/**
 * 统一响应结果封装
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> successWithMessage(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static Result<Void> success() {
        return new Result<>(200, "操作成功", null);
    }

    public static Result<Void> success(String message) {
        return new Result<>(200, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
