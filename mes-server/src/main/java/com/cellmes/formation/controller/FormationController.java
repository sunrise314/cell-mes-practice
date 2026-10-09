package com.cellmes.formation.controller;

import com.cellmes.common.api.R;
import com.cellmes.common.event.FormationCompleteEvent;
import com.cellmes.formation.entity.CapacityPrediction;
import com.cellmes.formation.service.CapacityPredictionService;
import com.cellmes.trace.service.LotService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 化成完成上报（ch10）：模拟化成柜报文到达。 */
@RestController
@RequestMapping("/api/formation")
@RequiredArgsConstructor
public class FormationController {

    private final CapacityPredictionService predictionService;
    private final LotService lotService;

    @Data
    public static class CompleteRequest {
        /** 二选一：lotId 或 lotNo */
        private Long lotId;
        private String lotNo;
        private String deviceCode;
        /** coatThicknessDevPct / fillQtyDevPct / cutoffCurrentMa / ambientTempDevC / selfDischargeMv */
        private Map<String, Object> features;
    }

    @PostMapping("/complete")
    public R<CapacityPrediction> complete(@RequestBody CompleteRequest req) {
        Long lotId = req.getLotId() != null ? req.getLotId() : lotService.getByLotNo(req.getLotNo()).getId();
        CapacityPrediction record = predictionService.onFormationComplete(new FormationCompleteEvent(
                com.cellmes.common.tenant.TenantContext.get(), lotId, req.getDeviceCode(), req.getFeatures()));
        return R.ok(record);
    }
}
