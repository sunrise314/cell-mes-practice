package com.cellmes.oee.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.oee.engine.OeeEngine;
import com.cellmes.oee.entity.DowntimeEvent;
import com.cellmes.oee.entity.ScadaStatusMinute;
import com.cellmes.oee.entity.ShiftCalendar;
import com.cellmes.oee.mapper.DowntimeEventMapper;
import com.cellmes.oee.mapper.ScadaStatusMinuteMapper;
import com.cellmes.oee.mapper.ShiftCalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** OEE 计算（ch07）：分钟桶 × 状态映射 × 班次日历 → TR88 三因子。 */
@Service
@RequiredArgsConstructor
public class OeeService {

    private final ScadaStatusMinuteMapper minuteMapper;
    private final ShiftCalendarMapper shiftCalendarMapper;
    private final DowntimeEventMapper downtimeEventMapper;
    private final StatusMappingService statusMappingService;

    @Value("${mes.oee.micro-stop-threshold-min:5}")
    private int microStopThresholdMin;

    public record ShiftOee(String deviceCode, LocalDate shiftDate, String shiftCode,
                           long calendarMinutes, OeeEngine.OeeResult result) {
    }

    /** 按设备 + 日期（可选班次）计算 OEE。 */
    public List<ShiftOee> compute(String deviceCode, LocalDate date, String shiftCode) {
        List<ShiftCalendar> shifts = shiftCalendarMapper.selectList(Wrappers.<ShiftCalendar>lambdaQuery()
                .eq(ShiftCalendar::getShiftDate, date)
                .eq(shiftCode != null, ShiftCalendar::getShiftCode, shiftCode)
                .orderByAsc(ShiftCalendar::getShiftCode));
        if (shifts.isEmpty()) {
            throw new BizException(404, "班次日历无记录: " + date + (shiftCode == null ? "" : " " + shiftCode));
        }
        return shifts.stream().map(shift -> computeShift(deviceCode, shift)).toList();
    }

    private ShiftOee computeShift(String deviceCode, ShiftCalendar shift) {
        List<ScadaStatusMinute> buckets = minuteMapper.selectList(Wrappers.<ScadaStatusMinute>lambdaQuery()
                .eq(ScadaStatusMinute::getDeviceCode, deviceCode)
                .eq(ScadaStatusMinute::getShiftDate, shift.getShiftDate())
                .eq(ScadaStatusMinute::getShiftCode, shift.getShiftCode())
                .orderByAsc(ScadaStatusMinute::getMinuteTs));

        // 每工序独立映射（ch07 纪律），未映射状态 fail-closed
        List<OeeEngine.Minute> minutes = buckets.stream()
                .map(b -> new OeeEngine.Minute(
                        statusMappingService.category(b.getProcessId(), b.getScadaStatus()),
                        b.getActualOutput() == null ? 0 : b.getActualOutput(),
                        b.getRejectQty() == null ? 0 : b.getRejectQty(),
                        b.getStandardRate() == null ? 0 : b.getStandardRate().doubleValue()))
                .toList();

        OeeEngine.OeeResult result = OeeEngine.compute(minutes, microStopThresholdMin);
        long calendarMinutes = Boolean.TRUE.equals(shift.getIsHoliday()) ? 0
                : ChronoUnit.MINUTES.between(shift.getStartTime(), shift.getEndTime());
        return new ShiftOee(deviceCode, shift.getShiftDate(), shift.getShiftCode(), calendarMinutes, result);
    }

    public List<DowntimeEvent> downtimes(String deviceCode, LocalDate date) {
        return downtimeEventMapper.selectList(Wrappers.<DowntimeEvent>lambdaQuery()
                .eq(DowntimeEvent::getDeviceCode, deviceCode)
                .apply("date(start_time) = {0}", date)
                .orderByAsc(DowntimeEvent::getStartTime));
    }
}
