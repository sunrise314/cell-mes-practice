package com.cellmes.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 统一响应包装。 */
@Data
@AllArgsConstructor
public class R<T> {

    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) {
        return new R<>(0, "ok", data);
    }

    public static R<Void> ok() {
        return new R<>(0, "ok", null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
