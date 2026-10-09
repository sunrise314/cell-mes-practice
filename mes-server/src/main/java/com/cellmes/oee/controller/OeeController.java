package com.cellmes.oee.controller;

import com.cellmes.common.api.R;
import com.cellmes.oee.entity.DowntimeEvent;
import com.cellmes.oee.service.OeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** OEE 查询（ch07）。 */
@RestController
@RequestMapping("/api/oee")
@RequiredArgsConstructor
public class OeeController {

    private final OeeService oeeService;

    /** GET /api/oee/BC-001?date=2025-12-15&shift=A */
    @GetMapping("/{deviceCode}")
    public R<List<OeeService.ShiftOee>> oee(@PathVariable String deviceCode,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                            @RequestParam(required = false) String shift) {
        return R.ok(oeeService.compute(deviceCode, date, shift));
    }

    @GetMapping("/{deviceCode}/downtimes")
    public R<List<DowntimeEvent>> downtimes(@PathVariable String deviceCode,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(oeeService.downtimes(deviceCode, date));
    }
}
