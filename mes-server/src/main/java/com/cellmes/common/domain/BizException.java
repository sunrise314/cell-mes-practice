package com.cellmes.common.domain;

import lombok.Getter;

/** 业务异常：fail-closed 哲学的统一出口，code 对应 HTTP 状态码。 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
