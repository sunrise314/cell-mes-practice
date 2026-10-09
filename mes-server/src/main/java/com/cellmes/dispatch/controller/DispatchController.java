package com.cellmes.dispatch.controller;

import com.cellmes.common.api.R;
import com.cellmes.dispatch.dto.DispatchChangeRequest;
import com.cellmes.dispatch.dto.DispatchChangeResult;
import com.cellmes.dispatch.service.DispatchChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 改派（ch05 四步法）。 */
@RestController
@RequestMapping("/api/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchChangeService dispatchChangeService;

    /** POST /api/dispatch/change —— dryRun=true 返回检查清单不落库；校验不过返回 409。 */
    @PostMapping("/change")
    public R<DispatchChangeResult> change(@RequestBody DispatchChangeRequest req) {
        return R.ok(dispatchChangeService.change(req));
    }
}
