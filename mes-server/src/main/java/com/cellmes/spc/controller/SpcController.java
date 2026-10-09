package com.cellmes.spc.controller;

import com.cellmes.common.api.R;
import com.cellmes.spc.entity.SpcChart;
import com.cellmes.spc.entity.SpcSample;
import com.cellmes.spc.service.SpcService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** SPC（ch08）。 */
@RestController
@RequestMapping("/api/spc")
@RequiredArgsConstructor
public class SpcController {

    private final SpcService spcService;

    /** 控制图详情 + 样本 + 当前违规清单（种子数据应报 rule3 趋势）。 */
    @GetMapping("/charts/{deviceCode}")
    public R<SpcService.ChartDetail> chart(@PathVariable String deviceCode,
                                           @RequestParam(required = false) String characteristic) {
        SpcChart chart = spcService.getChart(deviceCode, characteristic);
        return R.ok(spcService.detail(chart.getId()));
    }

    @Data
    public static class AddSampleRequest {
        private Double value;
        private Long lotId;
    }

    @PostMapping("/charts/{chartId}/samples")
    public R<SpcSample> addSample(@PathVariable Long chartId, @RequestBody AddSampleRequest req) {
        return R.ok(spcService.addSample(chartId, req.getValue(), req.getLotId()));
    }

    @PostMapping("/charts/{chartId}/recalculate")
    public R<SpcChart> recalculate(@PathVariable Long chartId) {
        return R.ok(spcService.recalculateBaseline(chartId));
    }
}
