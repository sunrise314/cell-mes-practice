package com.cellmes.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 改派结果：7 项前置校验清单 + 重展开摘要。 */
@Data
@AllArgsConstructor
public class DispatchChangeResult {

    private String woNo;
    private boolean dryRun;
    private boolean passed;
    private List<Check> checks;
    private Summary summary;

    @Data
    @AllArgsConstructor
    public static class Check {
        private String name;
        private boolean pass;
        private String detail;
    }

    @Data
    @AllArgsConstructor
    public static class Summary {
        /** 重展开后的参数集版本 */
        private int paramSetVersion;
        private int operations;
        private int bomLines;
        private String targetDevice;
    }
}
