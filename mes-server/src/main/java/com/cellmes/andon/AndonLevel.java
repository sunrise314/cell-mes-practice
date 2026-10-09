package com.cellmes.andon;

import lombok.Getter;

/** Andon 呼叫等级（ch09）：L1 操作工 1/5、L2 班组长 5/30、L3 工程师 15/120、L4 厂长 30/480（响应/到场 分钟）。 */
@Getter
public enum AndonLevel {

    L1(1, 5),
    L2(5, 30),
    L3(15, 120),
    L4(30, 480);

    /** 响应 SLA（分钟） */
    private final int responseMin;
    /** 到场 SLA（分钟） */
    private final int onsiteMin;

    AndonLevel(int responseMin, int onsiteMin) {
        this.responseMin = responseMin;
        this.onsiteMin = onsiteMin;
    }
}
