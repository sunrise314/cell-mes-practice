package com.cellmes.trace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.trace.entity.Lot;
import com.cellmes.trace.entity.MaterialMovement;
import com.cellmes.trace.mapper.LotMapper;
import com.cellmes.trace.mapper.MaterialMovementMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投料/收料服务（ch06）：追溯链物理上不可能断的三个纪律——
 * 1. from_lot 非空（上游批次强制扫描）；
 * 2. 余量校验（历史消耗累计 + 本次 ≤ 上游批次量）；
 * 3. prev 链接 + 回写上游 next（双向链表实时维护）。
 */
@Service
@RequiredArgsConstructor
public class MaterialReceiveService {

    private final LotMapper lotMapper;
    private final MaterialMovementMapper movementMapper;
    private final SnService snService;

    @Data
    public static class RecordRequest {
        private Long workOrderId;
        private Integer operationSeq;
        private String deviceCode;
        private String fromLotNo;
        /** SN 多阶段批次时必填（区分 FILLED_CELL/FORMED_CELL...） */
        private String fromLotType;
        private String toLotNo;
        private String toLotType;
        /** 不填且工序为注液(5)时自动生成 SN */
        private Boolean autoSn;
        private BigDecimal quantity;
        private String unit;
    }

    @Transactional
    public MaterialMovement record(RecordRequest req) {
        // 纪律 1：from_lot 非空
        if (req.getFromLotNo() == null || req.getFromLotNo().isBlank()) {
            throw new BizException("上游批次号不能为空（追溯链物理上不可能断）");
        }
        List<Lot> candidates = lotMapper.selectList(Wrappers.<Lot>lambdaQuery()
                .eq(Lot::getLotNo, req.getFromLotNo()));
        if (candidates.isEmpty()) {
            throw new BizException(404, "上游批次不存在: " + req.getFromLotNo());
        }
        Lot fromLot;
        if (candidates.size() > 1) {
            if (req.getFromLotType() == null) {
                throw new BizException("批次号命中多条（SN 多阶段），必须指定 fromLotType");
            }
            fromLot = candidates.stream()
                    .filter(l -> req.getFromLotType().equals(l.getLotType()))
                    .findFirst()
                    .orElseThrow(() -> new BizException(404,
                            "批次类型不匹配: " + req.getFromLotNo() + "/" + req.getFromLotType()));
        } else {
            fromLot = candidates.getFirst();
        }
        if ("HOLD".equals(fromLot.getStatus())) {
            throw new BizException(409, "批次已 HOLD，禁止投料: " + fromLot.getLotNo());
        }

        // 纪律 2：余量校验（sumConsumed + 本次 ≤ 上游批次量）
        BigDecimal required = req.getQuantity() == null ? BigDecimal.ZERO : req.getQuantity();
        BigDecimal consumed = sumConsumed(fromLot.getId());
        if (consumed.add(required).compareTo(fromLot.getQuantity()) > 0) {
            throw new BizException(409, "上游批次余量不足: " + fromLot.getLotNo()
                    + " 总量=" + fromLot.getQuantity() + " 已耗=" + consumed + " 本次=" + required);
        }

        // 产出批次：SN 自动生成（注液工序）或显式指定
        Lot toLot = resolveToLot(req, fromLot);

        // 纪律 3：prev 链接（上游批次最新一次「产出它的移动」）+ 回写上游 next
        MaterialMovement prev = movementMapper.selectOne(Wrappers.<MaterialMovement>lambdaQuery()
                .eq(MaterialMovement::getToLotId, fromLot.getId())
                .orderByDesc(MaterialMovement::getId)
                .last("limit 1"));

        MaterialMovement movement = new MaterialMovement();
        movement.setEventTime(LocalDateTime.now());
        movement.setFromLotId(fromLot.getId());
        movement.setFromLotType(fromLot.getLotType());
        movement.setWorkOrderId(req.getWorkOrderId());
        movement.setOperationSeq(req.getOperationSeq());
        movement.setDeviceCode(req.getDeviceCode());
        movement.setToLotId(toLot.getId());
        movement.setToLotType(toLot.getLotType());
        movement.setQuantity(required);
        movement.setUnit(req.getUnit() == null ? toLot.getUnit() : req.getUnit());
        movement.setPrevMovementId(prev == null ? null : prev.getId());
        movementMapper.insert(movement);

        if (prev != null) {
            prev.setNextMovementId(movement.getId());
            movementMapper.updateById(prev);
        }
        return movement;
    }

    /** 产出批次：优先复用已存在（同 SN 新阶段）；注液工序支持自动 SN（FILLED_CELL）。 */
    private Lot resolveToLot(RecordRequest req, Lot fromLot) {
        if (req.getToLotNo() == null || req.getToLotNo().isBlank()) {
            boolean isFill = req.getOperationSeq() != null && req.getOperationSeq() == 5;
            if (Boolean.TRUE.equals(req.getAutoSn()) && isFill) {
                String sn = snService.generateSn(fromLot.getItemId());
                return createLot(sn, "FILLED_CELL", fromLot.getItemId(), requiredOf(req), req.getUnit());
            }
            throw new BizException("必须指定产出批次 toLotNo（或注液工序 autoSn=true）");
        }
        List<Lot> existing = lotMapper.selectList(Wrappers.<Lot>lambdaQuery()
                .eq(Lot::getLotNo, req.getToLotNo())
                .eq(req.getToLotType() != null, Lot::getLotType, req.getToLotType()));
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        if (req.getToLotType() == null) {
            throw new BizException("新产出批次必须指定 toLotType");
        }
        return createLot(req.getToLotNo(), req.getToLotType(), fromLot.getItemId(), requiredOf(req), req.getUnit());
    }

    private BigDecimal requiredOf(RecordRequest req) {
        return req.getQuantity() == null ? BigDecimal.ZERO : req.getQuantity();
    }

    private Lot createLot(String lotNo, String lotType, Long itemId, BigDecimal quantity, String unit) {
        Lot lot = new Lot();
        lot.setLotNo(lotNo);
        lot.setLotType(lotType);
        lot.setItemId(itemId);
        lot.setQuantity(quantity);
        lot.setUnit(unit);
        lot.setStatus("IN_STOCK");
        lotMapper.insert(lot);
        return lot;
    }

    /** 历史消耗累计（该批次作为 from_lot 的所有移动之和）。 */
    private BigDecimal sumConsumed(Long fromLotId) {
        List<MaterialMovement> movements = movementMapper.selectList(Wrappers.<MaterialMovement>lambdaQuery()
                .eq(MaterialMovement::getFromLotId, fromLotId));
        return movements.stream()
                .map(m -> m.getQuantity() == null ? BigDecimal.ZERO : m.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
