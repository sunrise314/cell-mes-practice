package com.cellmes.dispatch.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 日志版采集适配器：教学骨架用日志模拟设备下发动作。 */
@Slf4j
@Component
public class LoggingCollectAdapter implements DeviceCollectAdapter {

    @Override
    public void dispatchCurve(String deviceCode, String curveCode, String paramsJson) {
        log.info("[化成曲线下发] device={} curve={} params={}", deviceCode, curveCode, paramsJson);
    }
}
