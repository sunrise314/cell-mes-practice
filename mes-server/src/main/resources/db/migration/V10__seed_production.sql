-- =====================================================================
-- V10: 生产链路种子 —— 工单展开 / 批次追溯链（102 条 movement）/ OEE 分钟桶 /
--      SPC 样本（rule3 趋势案例）/ 班次日历 / Andon 案例
-- 追溯链对齐 ch06 事故故事：LFP-PW-20251128-007 粉料批次 → 24 颗 SN → 成品/出货
-- =====================================================================

-- ---------- 工单（ch01 派工单展开实例） ----------
INSERT INTO work_order (id, tenant_id, wo_no, product_item_id, quantity, routing_id, bom_header_id, target_device_code, status) VALUES
(9001, 1, 'WO-20251215-018', 101, 1000, 1, 1, NULL,        'RELEASED'),  -- 追溯故事工单
(9002, 1, 'WO-20251204-001', 102, 1000, 2, 2, 'F1-012',    'CREATED'),   -- 改派主角：X-314 → X-280
(9003, 1, 'WO-20251204-002', 101, 500,  1, 1, NULL,        'RELEASED');  -- 改派拦截：seq1 已发料

-- WO-20251215-018 的 7 张派工单（work_cell 与追溯链 movement 的设备一致）
INSERT INTO work_order_operation (id, tenant_id, wo_no, seq, operation_name, resource_group_id, work_cell_code,
                                  shift_date, shift_code, planned_start, planned_end,
                                  standard_duration_min, process_params, param_set_version, status) VALUES
(9111, 1, 'WO-20251215-018', 1, '涂布', 1, 'BC-001', DATE '2025-12-15', 'A', TIMESTAMP '2025-12-15 08:00', TIMESTAMP '2025-12-15 09:30', 90,   '{}', 1, 'DONE'),
(9112, 1, 'WO-20251215-018', 2, '辊压', 2, 'RP-001', DATE '2025-12-15', 'A', TIMESTAMP '2025-12-15 09:30', TIMESTAMP '2025-12-15 10:36', 66,   '{}', 1, 'DONE'),
(9113, 1, 'WO-20251215-018', 3, '分切', 3, 'SL-001', DATE '2025-12-15', 'A', TIMESTAMP '2025-12-15 10:36', TIMESTAMP '2025-12-15 11:00', 24,   '{}', 1, 'DONE'),
(9114, 1, 'WO-20251215-018', 4, '卷绕', 4, 'WND-003', DATE '2025-12-15', 'A', TIMESTAMP '2025-12-15 11:00', TIMESTAMP '2025-12-15 13:06', 126,  '{}', 1, 'DONE'),
(9115, 1, 'WO-20251215-018', 5, '注液', 5, 'FL-001', DATE '2025-12-15', 'A', TIMESTAMP '2025-12-15 13:06', TIMESTAMP '2025-12-15 13:42', 36,   '{}', 1, 'DONE'),
(9116, 1, 'WO-20251215-018', 6, '化成', 6, 'F1-012', DATE '2025-12-15', 'A', TIMESTAMP '2025-12-15 13:42', TIMESTAMP '2025-12-16 13:42', 1440, '{"formation_curve":"FORN-280-CHG-V3"}', 1, 'DONE'),
(9117, 1, 'WO-20251215-018', 7, '分容', 7, 'FC-003', DATE '2025-12-16', 'A', TIMESTAMP '2025-12-16 13:42', TIMESTAMP '2025-12-17 01:42', 720,  '{}', 1, 'DONE');

-- WO-20251204-001（X-314，待改派）
INSERT INTO work_order_operation (id, tenant_id, wo_no, seq, operation_name, resource_group_id, work_cell_code,
                                  standard_duration_min, process_params, param_set_version, status) VALUES
(9211, 1, 'WO-20251204-001', 1, '涂布', 1, 'BC-002', 90,   '{}', 1, 'WAITING'),
(9212, 1, 'WO-20251204-001', 2, '辊压', 2, 'RP-001', 66,   '{}', 1, 'WAITING'),
(9213, 1, 'WO-20251204-001', 3, '分切', 3, 'SL-001', 24,   '{}', 1, 'WAITING'),
(9214, 1, 'WO-20251204-001', 4, '卷绕', 4, 'WND-001', 126, '{}', 1, 'WAITING'),
(9215, 1, 'WO-20251204-001', 5, '注液', 5, 'FL-001', 36,   '{}', 1, 'WAITING'),
(9216, 1, 'WO-20251204-001', 6, '化成', 6, 'F1-012', 1440, '{"formation_curve":"FORN-314-CHG-V2"}', 1, 'WAITING'),
(9217, 1, 'WO-20251204-001', 7, '分容', 7, 'FC-001', 720,  '{}', 1, 'WAITING');

-- WO-20251204-002（X-280，seq1 已发料 → 改派拦截演示）
INSERT INTO work_order_operation (id, tenant_id, wo_no, seq, operation_name, resource_group_id, work_cell_code,
                                  standard_duration_min, process_params, param_set_version, status) VALUES
(9311, 1, 'WO-20251204-002', 1, '涂布', 1, 'BC-001', 90, '{}', 1, 'ISSUED'),
(9312, 1, 'WO-20251204-002', 2, '辊压', 2, 'RP-001', 66, '{}', 1, 'WAITING'),
(9313, 1, 'WO-20251204-002', 3, '分切', 3, 'SL-001', 24, '{}', 1, 'WAITING'),
(9314, 1, 'WO-20251204-002', 4, '卷绕', 4, 'WND-002', 126, '{}', 1, 'WAITING'),
(9315, 1, 'WO-20251204-002', 5, '注液', 5, 'FL-001', 36, '{}', 1, 'WAITING'),
(9316, 1, 'WO-20251204-002', 6, '化成', 6, 'F1-011', 1440, '{"formation_curve":"FORN-280-CHG-V3"}', 1, 'WAITING'),
(9317, 1, 'WO-20251204-002', 7, '分容', 7, 'FC-002', 720, '{}', 1, 'WAITING');

-- ---------- 工单 BOM 行（模板行 × 工单数量） ----------
INSERT INTO work_order_bom_line (id, tenant_id, wo_no, line_no, child_item_id, quantity, unit, operation_seq, substitutes, status)
SELECT 9400 + line_no, tenant_id, 'WO-20251215-018', line_no, child_item_id, quantity * 1000, unit, operation_seq, substitutes, 'PENDING'
FROM bom_line WHERE tenant_id = 1 AND bom_header_id = 1;
INSERT INTO work_order_bom_line (id, tenant_id, wo_no, line_no, child_item_id, quantity, unit, operation_seq, substitutes, status)
SELECT 9420 + line_no, tenant_id, 'WO-20251204-001', line_no, child_item_id, quantity * 1000, unit, operation_seq, substitutes, 'PENDING'
FROM bom_line WHERE tenant_id = 1 AND bom_header_id = 2;
INSERT INTO work_order_bom_line (id, tenant_id, wo_no, line_no, child_item_id, quantity, unit, operation_seq, substitutes, status)
SELECT 9430 + line_no, tenant_id, 'WO-20251204-002', line_no, child_item_id, quantity * 500, unit, operation_seq, substitutes, 'ISSUED'
FROM bom_line WHERE tenant_id = 1 AND bom_header_id = 1;

-- ---------- 批次追溯链（ch06 双向链表 + 批次三链） ----------
DO $$
DECLARE
  v_tenant  CONSTANT INT := 1;
  v_wo      CONSTANT BIGINT := 9001;
  v_powder  BIGINT; v_ec BIGINT;
  v_coil BIGINT; v_rp BIGINT; v_sub1 BIGINT; v_sub2 BIGINT;
  v_m1 BIGINT; v_m2 BIGINT; v_m3a BIGINT; v_m3b BIGINT;
  v_m3_next BIGINT; v_m4x BIGINT; v_m5a BIGINT; v_m6 BIGINT; v_m7 BIGINT;
  v_sn TEXT; v_bare BIGINT; v_filled BIGINT; v_formed BIGINT; v_final BIGINT;
  v_br INT; v_j INT; v_i INT; v_sub BIGINT;
BEGIN
  -- 上游物料批次
  INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
  VALUES (v_tenant, 'LFP-PW-20251128-007', 'POWDER', 130, 128, 'kg', 'IN_STOCK')
  RETURNING id INTO v_powder;
  INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
  VALUES (v_tenant, 'EC-20251201-005', 'MATERIAL', 115, 50, 'kg', 'IN_STOCK')
  RETURNING id INTO v_ec;

  -- 涂布（粉 → 极片卷）
  INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
  VALUES (v_tenant, 'BC-001-20251215-003', 'COIL', 111, 3600, 'm', 'IN_STOCK')
  RETURNING id INTO v_coil;
  INSERT INTO material_movement (tenant_id, event_time, from_lot_id, from_lot_type, work_order_id, operation_seq,
                                 device_code, to_lot_id, to_lot_type, quantity, unit)
  VALUES (v_tenant, TIMESTAMP '2025-12-15 08:23', v_powder, 'POWDER', v_wo, 1, 'BC-001',
          v_coil, 'COIL', 3600, 'm')
  RETURNING id INTO v_m1;

  -- 辊压
  INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
  VALUES (v_tenant, 'RP-20251215-003', 'COIL', 111, 3600, 'm', 'IN_STOCK')
  RETURNING id INTO v_rp;
  INSERT INTO material_movement (tenant_id, event_time, from_lot_id, from_lot_type, work_order_id, operation_seq,
                                 device_code, to_lot_id, to_lot_type, quantity, unit, prev_movement_id)
  VALUES (v_tenant, TIMESTAMP '2025-12-15 10:15', v_coil, 'COIL', v_wo, 2, 'RP-001',
          v_rp, 'COIL', 3600, 'm', v_m1)
  RETURNING id INTO v_m2;
  UPDATE material_movement SET next_movement_id = v_m2 WHERE id = v_m1;

  -- 分切：1 卷 → 2 小卷（1→N 分叉，next 只挂第一个孩子，CTE 走 lot 关联补全）
  INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
  VALUES (v_tenant, 'SL-20251215-003-01', 'SUBCOIL', 111, 1800, 'm', 'IN_STOCK')
  RETURNING id INTO v_sub1;
  INSERT INTO material_movement (tenant_id, event_time, from_lot_id, from_lot_type, work_order_id, operation_seq,
                                 device_code, to_lot_id, to_lot_type, quantity, unit, prev_movement_id)
  VALUES (v_tenant, TIMESTAMP '2025-12-15 11:30', v_rp, 'COIL', v_wo, 3, 'SL-001',
          v_sub1, 'SUBCOIL', 1800, 'm', v_m2)
  RETURNING id INTO v_m3a;
  INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
  VALUES (v_tenant, 'SL-20251215-003-02', 'SUBCOIL', 111, 1800, 'm', 'IN_STOCK')
  RETURNING id INTO v_sub2;
  INSERT INTO material_movement (tenant_id, event_time, from_lot_id, from_lot_type, work_order_id, operation_seq,
                                 device_code, to_lot_id, to_lot_type, quantity, unit, prev_movement_id)
  VALUES (v_tenant, TIMESTAMP '2025-12-15 11:31', v_rp, 'COIL', v_wo, 3, 'SL-001',
          v_sub2, 'SUBCOIL', 1800, 'm', v_m2)
  RETURNING id INTO v_m3b;
  UPDATE material_movement SET next_movement_id = v_m3a WHERE id = v_m2;

  -- 卷绕/注液/化成/分容：每小卷 12 颗 SN，SN 全程不变（同 SN 不同 lot_type）
  FOR v_br IN 1..2 LOOP
    v_sub := CASE WHEN v_br = 1 THEN v_sub1 ELSE v_sub2 END;
    v_m3_next := CASE WHEN v_br = 1 THEN v_m3a ELSE v_m3b END;
    FOR v_j IN 1..12 LOOP
      v_i := (v_br - 1) * 12 + v_j;
      v_sn := 'X280A20251215' || lpad(v_i::text, 4, '0');

      INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
      VALUES (v_tenant, v_sn, 'BARE_CELL', 101, 1, 'pcs', 'IN_STOCK')
      RETURNING id INTO v_bare;
      INSERT INTO material_movement (tenant_id, event_time, from_lot_id, from_lot_type, work_order_id, operation_seq,
                                     device_code, to_lot_id, to_lot_type, quantity, unit, prev_movement_id)
      VALUES (v_tenant, TIMESTAMP '2025-12-15 14:30', v_sub, 'SUBCOIL', v_wo, 4, 'WND-003',
              v_bare, 'BARE_CELL', 1, 'pcs', v_m3_next)
      RETURNING id INTO v_m4x;
      UPDATE material_movement SET next_movement_id = v_m4x
       WHERE id = v_m3_next AND next_movement_id IS NULL;

      -- 注液：裸芯 → 注液后电芯（同 SN）；电解液投入为并行支路（prev/next 置空，CTE 走 lot 关联）
      INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
      VALUES (v_tenant, v_sn, 'FILLED_CELL', 101, 1, 'pcs', 'IN_STOCK')
      RETURNING id INTO v_filled;
      INSERT INTO material_movement (tenant_id, event_time, from_lot_id, from_lot_type, work_order_id, operation_seq,
                                     device_code, to_lot_id, to_lot_type, quantity, unit, prev_movement_id)
      VALUES (v_tenant, TIMESTAMP '2025-12-15 16:00', v_bare, 'BARE_CELL', v_wo, 5, 'FL-001',
              v_filled, 'FILLED_CELL', 1, 'pcs', v_m4x)
      RETURNING id INTO v_m5a;
      UPDATE material_movement SET next_movement_id = v_m5a WHERE id = v_m4x;
      INSERT INTO material_movement (tenant_id, event_time, from_lot_id, from_lot_type, work_order_id, operation_seq,
                                     device_code, to_lot_id, to_lot_type, quantity, unit)
      VALUES (v_tenant, TIMESTAMP '2025-12-15 16:00', v_ec, 'MATERIAL', v_wo, 5, 'FL-001',
              v_filled, 'FILLED_CELL', 0.145, 'kg');

      -- 化成（同 SN）
      INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
      VALUES (v_tenant, v_sn, 'FORMED_CELL', 101, 1, 'pcs', 'IN_STOCK')
      RETURNING id INTO v_formed;
      INSERT INTO material_movement (tenant_id, event_time, from_lot_id, from_lot_type, work_order_id, operation_seq,
                                     device_code, to_lot_id, to_lot_type, quantity, unit, prev_movement_id)
      VALUES (v_tenant, TIMESTAMP '2025-12-15 22:00', v_filled, 'FILLED_CELL', v_wo, 6, 'F1-012',
              v_formed, 'FORMED_CELL', 1, 'pcs', v_m5a)
      RETURNING id INTO v_m6;
      UPDATE material_movement SET next_movement_id = v_m6 WHERE id = v_m5a;

      -- 分容（同 SN）
      INSERT INTO lot (tenant_id, lot_no, lot_type, item_id, quantity, unit, status)
      VALUES (v_tenant, v_sn, 'FINAL_CELL', 101, 1, 'pcs', 'IN_STOCK')
      RETURNING id INTO v_final;
      INSERT INTO material_movement (tenant_id, event_time, from_lot_id, from_lot_type, work_order_id, operation_seq,
                                     device_code, to_lot_id, to_lot_type, quantity, unit, prev_movement_id)
      VALUES (v_tenant, TIMESTAMP '2025-12-16 10:00', v_formed, 'FORMED_CELL', v_wo, 7, 'FC-003',
              v_final, 'FINAL_CELL', 1, 'pcs', v_m6)
      RETURNING id INTO v_m7;
      UPDATE material_movement SET next_movement_id = v_m7 WHERE id = v_m6;
    END LOOP;
  END LOOP;

  -- 消耗状态收敛
  UPDATE lot SET status = 'CONSUMED' WHERE tenant_id = v_tenant
    AND (lot_no = 'LFP-PW-20251128-007' OR lot_no LIKE 'BC-001-%' OR lot_no LIKE 'RP-%'
         OR lot_no LIKE 'SL-%' OR lot_no LIKE 'X280A20251215%')
    AND lot_type <> 'FINAL_CELL';
  -- 出货批次：0001~0010 + 0023（客诉 SN）
  UPDATE lot SET status = 'SHIPPED' WHERE tenant_id = v_tenant AND lot_type = 'FINAL_CELL'
    AND right(lot_no, 4) IN ('0001','0002','0003','0004','0005','0006','0007','0008','0009','0010','0023');
END $$;

-- ---------- 班次日历（ch07：开始时间归属原则，24:00 归 C 班） ----------
INSERT INTO shift_calendar (tenant_id, shift_date, shift_code, start_time, end_time, crew_id, is_holiday)
SELECT 1, d, 'A', d + TIME '08:00', d + TIME '16:00', 1, d = DATE '2025-12-25'
FROM generate_series(DATE '2025-12-15', DATE '2025-12-25', INTERVAL '1 day') AS d;
INSERT INTO shift_calendar (tenant_id, shift_date, shift_code, start_time, end_time, crew_id, is_holiday)
SELECT 1, d, 'B', d + TIME '16:00', d + TIME '24:00', 2, d = DATE '2025-12-25'
FROM generate_series(DATE '2025-12-15', DATE '2025-12-25', INTERVAL '1 day') AS d;
INSERT INTO shift_calendar (tenant_id, shift_date, shift_code, start_time, end_time, crew_id, is_holiday)
SELECT 1, d, 'C', d + TIME '00:00', d + TIME '08:00', 3, d = DATE '2025-12-25'
FROM generate_series(DATE '2025-12-15', DATE '2025-12-25', INTERVAL '1 day') AS d;

-- ---------- 状态映射（ch07：每工序独立，SCADA → 时间类别） ----------
INSERT INTO status_mapping (tenant_id, process_id, scada_status, time_category) VALUES
(1, 1, 'RUN',      'run_time'),
(1, 1, 'IDLE',     'idle_time'),
(1, 1, 'PM',       'planned_down'),
(1, 1, 'CHG',      'planned_down'),
(1, 1, 'MAT_WAIT', 'unplanned_down'),
(1, 1, 'ALARM',    'unplanned_down'),
(1, 1, 'MECH',     'unplanned_down'),
(1, 1, 'ELEC',     'unplanned_down'),
(1, 6, 'CHARGE',   'run_time'),
(1, 6, 'STANDBY',  'idle_time'),
(1, 6, 'ALARM',    'unplanned_down'),
(1, 2, 'RUN',      'run_time'),
(1, 2, 'IDLE',     'idle_time'),
(1, 2, 'MAT_WAIT', 'unplanned_down'),
(1, 3, 'RUN',      'run_time'),
(1, 3, 'IDLE',     'idle_time'),
(1, 3, 'MAT_WAIT', 'unplanned_down'),
(1, 4, 'RUN',      'run_time'),
(1, 4, 'IDLE',     'idle_time'),
(1, 4, 'MAT_WAIT', 'unplanned_down'),
(1, 5, 'RUN',      'run_time'),
(1, 5, 'IDLE',     'idle_time'),
(1, 5, 'MAT_WAIT', 'unplanned_down'),
(1, 7, 'RUN',      'run_time'),
(1, 7, 'IDLE',     'idle_time'),
(1, 7, 'MAT_WAIT', 'unplanned_down');

-- ---------- 分钟桶（ch07：BC-001 A 班 480 分钟；微停 3+2+2、MAT_WAIT 60、PM 30、IDLE 11 → RUN 372） ----------
INSERT INTO scada_status_minute (tenant_id, device_code, process_id, shift_date, shift_code, minute_ts,
                                 scada_status, actual_output, reject_qty, standard_rate)
SELECT 1, 'BC-001', 1, DATE '2025-12-15', 'A',
       TIMESTAMP '2025-12-15 08:00' + (i || ' minutes')::interval,
       st.st,
       CASE WHEN st.st = 'RUN' THEN 1 ELSE 0 END,
       CASE WHEN st.st = 'RUN' AND i BETWEEN 50 AND 55 THEN 1 ELSE 0 END,
       1.0
FROM generate_series(0, 479) AS i
CROSS JOIN LATERAL (
  SELECT CASE
    WHEN i BETWEEN 120 AND 122 THEN 'IDLE'
    WHEN i BETWEEN 200 AND 201 THEN 'IDLE'
    WHEN i BETWEEN 260 AND 319 THEN 'MAT_WAIT'
    WHEN i BETWEEN 360 AND 389 THEN 'PM'
    WHEN i BETWEEN 400 AND 401 THEN 'IDLE'
    WHEN i BETWEEN 430 AND 440 THEN 'IDLE'
    ELSE 'RUN' END AS st
) st;

-- 停机事件（与分钟桶一致）
INSERT INTO downtime_event (tenant_id, device_code, start_time, end_time, duration_min,
                            reason_level1, reason_level2, reason_level3, operator_id, shift_code)
VALUES (1, 'BC-001', TIMESTAMP '2025-12-15 12:20', TIMESTAMP '2025-12-15 13:20', 60,
        '待料', '上工序缺料', '浆料罐清洗超时', 101, 'A');

-- ---------- SPC（ch08：F1-012 化成容量图；末 7 点单调下降触发西电规则 3） ----------
INSERT INTO spc_chart (id, tenant_id, device_code, process_id, characteristic, subgroup_size,
                       spec_lower, spec_upper, target, control_lower, control_upper,
                       mean_value, sigma_value, cp, cpk, ppk, baseline_status, baseline_since)
VALUES (1, 1, 'F1-012', 6, 'capacity', 1, 275.0, 285.0, 280.0, 277.0, 283.0,
        280.0, 1.0, NULL, NULL, NULL, 'ACTIVE', TIMESTAMP '2025-12-01 00:00');

-- 30 个样本：前 23 个混入超出 ±1σ 的点（防误触 rule7），后 7 点严格递减 → rule3
INSERT INTO spc_sample (tenant_id, chart_id, sample_time, subgroup_no, sample_value, lot_id)
SELECT 1, 1, TIMESTAMP '2025-12-13 08:00' + (g || ' hours')::interval, g, v.val,
       CASE WHEN g = 28 THEN (SELECT id FROM lot WHERE tenant_id = 1 AND lot_no = 'X280A202512150023' AND lot_type = 'FILLED_CELL')
            WHEN g = 29 THEN (SELECT id FROM lot WHERE tenant_id = 1 AND lot_no = 'X280A202512150023' AND lot_type = 'FORMED_CELL')
            WHEN g = 30 THEN (SELECT id FROM lot WHERE tenant_id = 1 AND lot_no = 'X280A202512150023' AND lot_type = 'FINAL_CELL')
            ELSE NULL END
FROM (VALUES (1,280.4),(2,279.6),(3,281.4),(4,279.8),(5,280.5),(6,278.6),(7,280.1),(8,279.9),
             (9,280.6),(10,278.5),(11,280.3),(12,279.7),(13,281.2),(14,279.8),(15,280.4),
             (16,278.7),(17,280.2),(18,279.9),(19,280.5),(20,279.5),(21,281.3),(22,279.8),
             (23,280.3),(24,280.2),(25,280.0),(26,279.8),(27,279.6),(28,279.4),(29,279.2),
             (30,279.0)) AS v(g, val);

-- ---------- Andon 案例（ch09） ----------
INSERT INTO andon_call (tenant_id, call_time, call_source, call_level, device_code, process_id,
                        caller_id, title, description, status, sla_response_min, sla_onsite_min,
                        sla_response_deadline, sla_onsite_deadline)
VALUES (1, TIMESTAMP '2025-12-15 10:00', 'MANUAL', 'L4', NULL, NULL, 201,
        'X-314 改派需求', '车厂追加 314Ah 订单，评估全链改派影响', 'OPEN', 30, 480,
        TIMESTAMP '2025-12-15 10:30', TIMESTAMP '2025-12-15 18:00');

-- ---------- 序列校正 ----------
SELECT setval(pg_get_serial_sequence('work_order','id'),           (SELECT MAX(id) FROM work_order));
SELECT setval(pg_get_serial_sequence('work_order_operation','id'), (SELECT MAX(id) FROM work_order_operation));
SELECT setval(pg_get_serial_sequence('work_order_bom_line','id'),  (SELECT MAX(id) FROM work_order_bom_line));
SELECT setval(pg_get_serial_sequence('lot','id'),                  (SELECT MAX(id) FROM lot));
SELECT setval(pg_get_serial_sequence('material_movement','id'),    (SELECT MAX(id) FROM material_movement));
SELECT setval(pg_get_serial_sequence('shift_calendar','id'),       (SELECT MAX(id) FROM shift_calendar));
SELECT setval(pg_get_serial_sequence('status_mapping','id'),       (SELECT MAX(id) FROM status_mapping));
SELECT setval(pg_get_serial_sequence('scada_status_minute','id'),  (SELECT MAX(id) FROM scada_status_minute));
SELECT setval(pg_get_serial_sequence('downtime_event','id'),       (SELECT MAX(id) FROM downtime_event));
SELECT setval(pg_get_serial_sequence('spc_chart','id'),            (SELECT MAX(id) FROM spc_chart));
SELECT setval(pg_get_serial_sequence('spc_sample','id'),           (SELECT MAX(id) FROM spc_sample));
SELECT setval(pg_get_serial_sequence('andon_call','id'),           (SELECT MAX(id) FROM andon_call));
