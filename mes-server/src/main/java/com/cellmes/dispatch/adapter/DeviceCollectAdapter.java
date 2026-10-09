package com.cellmes.dispatch.adapter;

/** 设备采集适配器（ch05）：下发化成曲线到设备的端口，未来对接真实PLC/网关只换实现。 */
public interface DeviceCollectAdapter {

    /**
     * @param deviceCode 设备编码（WorkCell）
     * @param curveCode  化成曲线编码（设备配方 code，如 FORN-280-CHG-V3）
     * @param paramsJson 曲线参数 JSON
     */
    void dispatchCurve(String deviceCode, String curveCode, String paramsJson);
}
