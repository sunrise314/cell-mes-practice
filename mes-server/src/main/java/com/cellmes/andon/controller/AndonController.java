package com.cellmes.andon.controller;

import com.cellmes.andon.AndonLevel;
import com.cellmes.andon.entity.AndonCall;
import com.cellmes.andon.entity.EightDCase;
import com.cellmes.andon.service.AndonService;
import com.cellmes.andon.service.EightDService;
import com.cellmes.common.api.R;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Andon（ch09）：手动/设备报警/客诉入口 + 响应/解决 + 升级扫描 + 8D 推进。 */
@RestController
@RequestMapping("/api/andon")
@RequiredArgsConstructor
public class AndonController {

    private final AndonService andonService;
    private final EightDService eightDService;

    public record CreateCallRequest(String level, String deviceCode, Integer processId, Long lotId,
                                    String title, String description) {
    }

    @PostMapping("/calls")
    public R<AndonCall> manual(@RequestBody CreateCallRequest req) {
        return R.ok(andonService.createCall("MANUAL", AndonLevel.valueOf(req.level()),
                req.deviceCode(), req.processId(), req.lotId(), req.title(), req.description()));
    }

    @Data
    public static class DeviceAlarmRequest {
        private String deviceCode;
        private Integer processId;
        private String alarmCode;
        private String message;
    }

    /** 设备报警入口：按报警严重度映射 L2/L3。 */
    @PostMapping("/device-alarm")
    public R<AndonCall> deviceAlarm(@RequestBody DeviceAlarmRequest req) {
        AndonLevel level = req.getAlarmCode() != null && req.getAlarmCode().startsWith("CRIT")
                ? AndonLevel.L2 : AndonLevel.L3;
        return R.ok(andonService.createCall("DEVICE", level, req.getDeviceCode(), req.getProcessId(),
                null, "设备报警 " + req.getAlarmCode(), req.getMessage()));
    }

    @Data
    public static class ComplaintRequest {
        private String title;
        private String description;
    }

    /** 客诉入口：自动 Andon L3 + 8D 开案。 */
    @PostMapping("/customer-complaint")
    public R<EightDCase> complaint(@RequestBody ComplaintRequest req) {
        return R.ok(eightDService.openCustomerComplaint(req.getTitle(), req.getDescription()));
    }

    @GetMapping("/calls")
    public R<List<AndonCall>> calls(@RequestParam(required = false) String status) {
        return R.ok(andonService.list(status));
    }

    @PostMapping("/calls/{id}/respond")
    public R<AndonCall> respond(@PathVariable Long id, @RequestParam Long userId) {
        return R.ok(andonService.respond(id, userId));
    }

    @PostMapping("/calls/{id}/resolve")
    public R<AndonCall> resolve(@PathVariable Long id, @RequestParam Long userId) {
        return R.ok(andonService.resolve(id, userId));
    }

    /** 手动触发一次超时升级扫描（演示用）。 */
    @PostMapping("/escalate-scan")
    public R<Map<String, Integer>> escalateScan() {
        return R.ok(Map.of("escalated", andonService.escalateOverdueNow()));
    }

    @GetMapping("/8d")
    public R<List<EightDCase>> cases(@RequestParam(required = false) String status) {
        return R.ok(eightDService.list(status));
    }

    @PostMapping("/8d/{id}/advance")
    public R<EightDCase> advance(@PathVariable Long id, @RequestBody EightDService.StepRequest req) {
        return R.ok(eightDService.advance(id, req));
    }
}
