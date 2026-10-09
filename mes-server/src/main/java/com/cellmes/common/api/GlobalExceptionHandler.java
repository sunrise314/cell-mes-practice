package com.cellmes.common.api;

import com.cellmes.common.domain.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按异常携带的 code 返回对应 HTTP 状态。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getCode()).body(R.fail(e.getCode(), e.getMessage()));
    }

    /** 参数校验失败（@Valid）：取第一个字段错误。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(R.fail(400, msg));
    }

    /** 兜底：未知异常一律 500，不向客户端泄漏堆栈。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.internalServerError().body(R.fail(500, "系统繁忙，请稍后重试"));
    }
}
