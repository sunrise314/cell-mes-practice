package com.cellmes.trace.dao;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.cellmes.trace.entity.Lot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 追溯 DAO（ch06）：手写递归 CTE，显式携带 tenant_id（批注 @InterceptorIgnore 豁免拦截器）。
 * 豁免原因：拦截器无法重写 WITH 内的 SQL，租户条件必须手写，防止全表穿越。
 * 1→N 分叉（分切一卷出多小卷）不依赖 prev/next 链，靠 to_lot_id/from_lot_id 的 lot 关联自然展开。
 */
@InterceptorIgnore(tenantLine = "true")
public interface TraceDao {

    String TRACE_COLUMNS = """
            t.id AS movement_id, t.depth, t.operation_seq, t.device_code, t.work_order_id,
            t.quantity, t.unit, t.event_time,
            f.id AS from_lot_id, f.lot_no AS from_lot_no, f.lot_type AS from_lot_type,
            tt.id AS to_lot_id, tt.lot_no AS to_lot_no, tt.lot_type AS to_lot_type
            """;

    String TRACE_JOIN = """
            FROM trace t
            JOIN lot f  ON f.id  = t.from_lot_id AND f.tenant_id  = #{tenantId}
            JOIN lot tt ON tt.id = t.to_lot_id   AND tt.tenant_id = #{tenantId}
            ORDER BY t.depth, t.id
            """;

    /**
     * 正向追溯（追根源）：从 to_lot_id = lotId 的移动出发，
     * 沿 m.to_lot_id = t.from_lot_id 一路回溯到原料批次。
     */
    @Select("""
            WITH RECURSIVE trace AS (
                SELECT m.id, 1 AS depth, m.operation_seq, m.device_code, m.work_order_id,
                       m.quantity, m.unit, m.event_time,
                       m.from_lot_id, m.to_lot_id
                FROM material_movement m
                WHERE m.tenant_id = #{tenantId} AND m.to_lot_id = #{lotId}
              UNION ALL
                SELECT m.id, t.depth + 1, m.operation_seq, m.device_code, m.work_order_id,
                       m.quantity, m.unit, m.event_time,
                       m.from_lot_id, m.to_lot_id
                FROM material_movement m
                JOIN trace t ON m.to_lot_id = t.from_lot_id
                WHERE m.tenant_id = #{tenantId} AND t.depth < #{maxDepth}
            )
            SELECT""" + " " + TRACE_COLUMNS + TRACE_JOIN)
    List<TraceEdge> traceBackward(@Param("tenantId") Long tenantId,
                                  @Param("lotId") Long lotId,
                                  @Param("maxDepth") int maxDepth);

    /**
     * 反向追溯（追影响面）：从 from_lot_id = lotId 的移动出发，
     * 沿 m.from_lot_id = t.to_lot_id 一路向下游扩散。
     */
    @Select("""
            WITH RECURSIVE trace AS (
                SELECT m.id, 1 AS depth, m.operation_seq, m.device_code, m.work_order_id,
                       m.quantity, m.unit, m.event_time,
                       m.from_lot_id, m.to_lot_id
                FROM material_movement m
                WHERE m.tenant_id = #{tenantId} AND m.from_lot_id = #{lotId}
              UNION ALL
                SELECT m.id, t.depth + 1, m.operation_seq, m.device_code, m.work_order_id,
                       m.quantity, m.unit, m.event_time,
                       m.from_lot_id, m.to_lot_id
                FROM material_movement m
                JOIN trace t ON m.from_lot_id = t.to_lot_id
                WHERE m.tenant_id = #{tenantId} AND t.depth < #{maxDepth}
            )
            SELECT""" + " " + TRACE_COLUMNS + TRACE_JOIN)
    List<TraceEdge> traceForward(@Param("tenantId") Long tenantId,
                                 @Param("lotId") Long lotId,
                                 @Param("maxDepth") int maxDepth);

    /**
     * 同化成柜 + 同化成曲线的成品批次（sameFormationCurve 召回规则）：
     * 曲线存在 work_order_operation.process_params->>'formation_curve'（JSONB）。
     */
    @Select("""
            WITH src AS (
                SELECT m.id AS movement_id, m.device_code, m.work_order_id
                FROM material_movement m
                WHERE m.tenant_id = #{tenantId} AND m.id = #{seq6MovementId}
            )
            SELECT DISTINCT f.*
            FROM src
            JOIN work_order w1 ON w1.id = src.work_order_id AND w1.tenant_id = #{tenantId}
            JOIN work_order_operation wop1
                 ON wop1.tenant_id = #{tenantId} AND wop1.wo_no = w1.wo_no AND wop1.seq = 6
            JOIN material_movement m2
                 ON m2.tenant_id = #{tenantId} AND m2.operation_seq = 6 AND m2.device_code = src.device_code
            JOIN work_order w2 ON w2.id = m2.work_order_id AND w2.tenant_id = #{tenantId}
            JOIN work_order_operation wop2
                 ON wop2.tenant_id = #{tenantId} AND wop2.wo_no = w2.wo_no AND wop2.seq = 6
            JOIN lot fm ON fm.id = m2.to_lot_id AND fm.tenant_id = #{tenantId} AND fm.lot_type = 'FORMED_CELL'
            JOIN lot f  ON f.tenant_id = #{tenantId} AND f.lot_no = fm.lot_no AND f.lot_type = 'FINAL_CELL'
            WHERE wop2.process_params->>'formation_curve' = wop1.process_params->>'formation_curve'
            """)
    List<Lot> findFinalLotsSameFormationCurve(@Param("tenantId") Long tenantId,
                                              @Param("seq6MovementId") Long seq6MovementId);
}
