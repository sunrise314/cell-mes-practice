package com.cellmes.oee.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.oee.entity.StatusMapping;
import com.cellmes.oee.mapper.StatusMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 状态映射服务（ch07）：未映射状态 fail-closed 抛异常，禁止静默归入任何时间类别。 */
@Service
@RequiredArgsConstructor
public class StatusMappingService {

    private final StatusMappingMapper statusMappingMapper;

    public String category(Integer processId, String scadaStatus) {
        StatusMapping mapping = statusMappingMapper.selectOne(Wrappers.<StatusMapping>lambdaQuery()
                .eq(StatusMapping::getProcessId, processId)
                .eq(StatusMapping::getScadaStatus, scadaStatus)
                .last("limit 1"));
        if (mapping == null) {
            throw new BizException(409, "未映射的 SCADA 状态（fail-closed）: process=" + processId
                    + ", status=" + scadaStatus + "，请先在 status_mapping 配置");
        }
        return mapping.getTimeCategory();
    }
}
