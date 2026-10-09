package com.cellmes.trace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.trace.entity.Lot;
import com.cellmes.trace.mapper.LotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 批次服务（ch06）：状态流转（IN_STOCK/CONSUMED/SHIPPED/HOLD）。 */
@Service
@RequiredArgsConstructor
public class LotService {

    private final LotMapper lotMapper;

    public Lot getById(Long id) {
        Lot lot = lotMapper.selectById(id);
        if (lot == null) {
            throw new BizException(404, "批次不存在: id=" + id);
        }
        return lot;
    }

    /**
     * 按批次号解析批次。SN 贯穿多阶段（同 lot_no 不同 lot_type）：
     * 多条命中时优先返回 FINAL_CELL，其次按类型序。
     */
    public Lot getByLotNo(String lotNo) {
        List<Lot> lots = lotMapper.selectList(Wrappers.<Lot>lambdaQuery().eq(Lot::getLotNo, lotNo));
        if (lots.isEmpty()) {
            throw new BizException(404, "批次不存在: " + lotNo);
        }
        return lots.stream()
                .filter(l -> "FINAL_CELL".equals(l.getLotType()))
                .findFirst()
                .orElse(lots.getFirst());
    }

    public List<Lot> listByLotNo(String lotNo) {
        return lotMapper.selectList(Wrappers.<Lot>lambdaQuery().eq(Lot::getLotNo, lotNo));
    }

    public void hold(Long lotId) {
        Lot lot = getById(lotId);
        lot.setStatus("HOLD");
        lotMapper.updateById(lot);
    }

    public void holdByLotNos(List<String> lotNos) {
        for (String lotNo : lotNos) {
            for (Lot lot : listByLotNo(lotNo)) {
                if (!"HOLD".equals(lot.getStatus())) {
                    lot.setStatus("HOLD");
                    lotMapper.updateById(lot);
                }
            }
        }
    }
}
