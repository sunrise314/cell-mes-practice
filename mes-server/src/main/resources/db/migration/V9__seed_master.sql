-- =====================================================================
-- V9: 主数据种子 —— 电芯产线 7 段工艺（涂布→辊压→分切→卷绕→注液→化成→分容）
-- 数据口径对齐 ch01「7 段工艺真实节拍」与 ch05「X-280/X-314 三件套」
-- =====================================================================

-- ---------- 租户（ch02 多工厂） ----------
INSERT INTO tenant (id, code, name) VALUES
(1, 'YRD', 'CellCo 长三角工厂'),
(2, 'NW',  'CellCo 西北工厂');

-- ---------- 物料 ----------
INSERT INTO item (id, tenant_id, code, name, item_type, capacity_spec) VALUES
(101, 1, 'X-280',        '280Ah 半固态电芯',   'PRODUCT',  '280Ah'),
(102, 1, 'X-314',        '314Ah 高镍电芯',     'PRODUCT',  '314Ah'),
(103, 1, 'X-990-PROTO',  'X-990 原型电芯(未发布)', 'PRODUCT', '280Ah'),
(111, 1, 'LFP-POS-280',  '正极极片 280',       'MATERIAL', NULL),
(112, 1, 'LFP-POS-314',  '正极极片 314',       'MATERIAL', NULL),
(113, 1, 'GR-NEG-280',   '负极极片 280',       'MATERIAL', NULL),
(114, 1, 'SEP-280',      '隔膜 280',           'MATERIAL', NULL),
(115, 1, 'EC-280',       '电解液 280',         'MATERIAL', NULL),
(116, 1, 'CASE-280',     '铝壳 280',           'MATERIAL', NULL),
(117, 1, 'LID-280',      '顶盖 280',           'MATERIAL', NULL),
(118, 1, 'PILLAR-280',   '极柱 280',           'MATERIAL', NULL),
(121, 1, 'GR-NEG-314',   '负极极片 314',       'MATERIAL', NULL),
(122, 1, 'SEP-314',      '隔膜 314',           'MATERIAL', NULL),
(123, 1, 'EC-314',       '电解液 314',         'MATERIAL', NULL),
(124, 1, 'CASE-314',     '铝壳 314',           'MATERIAL', NULL),
(125, 1, 'LID-314',      '顶盖 314',           'MATERIAL', NULL),
(126, 1, 'PILLAR-314',   '极柱 314',           'MATERIAL', NULL),
(130, 1, 'LFP-POWDER-280','正极粉料 280',      'MATERIAL', NULL),
(201, 2, 'X-280-NW',     '280Ah 半固态电芯(西北)', 'PRODUCT', '280Ah');

-- ---------- ISA-95 层级：Area → WorkCenter → WorkCell ----------
INSERT INTO work_center (id, tenant_id, code, name, area) VALUES
(1, 1, 'WC-COAT',  '涂布 1 线', '极片车间'),
(2, 1, 'WC-ROLL',  '辊压 1 线', '极片车间'),
(3, 1, 'WC-SLIT',  '分切 1 线', '极片车间'),
(4, 1, 'WC-WIND',  '卷绕 1 线', '装配车间'),
(5, 1, 'WC-FILL',  '注液 1 线', '装配车间'),
(6, 1, 'WC-FORM',  '化成 1 区', '化成车间'),
(7, 1, 'WC-GRADE', '分容 1 区', '化成车间'),
(11, 2, 'WC-NW-COAT', '西北涂布线', '西北极片车间');

INSERT INTO work_cell (id, tenant_id, code, name, work_center_id, model, status) VALUES
(101, 1, 'BC-001', '涂布机 BC-001', 1, 'BC-1600', 'IDLE'),
(102, 1, 'BC-002', '涂布机 BC-002', 1, 'BC-1600', 'IDLE'),
(111, 1, 'RP-001', '辊压机 RP-001', 2, 'RP-1200', 'IDLE'),
(121, 1, 'SL-001', '分切机 SL-001', 3, 'SL-2000', 'IDLE'),
(131, 1, 'WND-001', '卷绕机 WND-001', 4, 'WND-800', 'IDLE'),
(132, 1, 'WND-002', '卷绕机 WND-002', 4, 'WND-800', 'IDLE'),
(133, 1, 'WND-003', '卷绕机 WND-003', 4, 'WND-800', 'IDLE'),
(141, 1, 'FL-001', '注液机 FL-001', 5, 'FL-500',  'IDLE'),
(151, 1, 'F1-001', '化成柜 F1-001', 6, 'F1-96G',  'IDLE'),
(152, 1, 'F1-002', '化成柜 F1-002', 6, 'F1-96G',  'IDLE'),
(153, 1, 'F1-003', '化成柜 F1-003', 6, 'F1-96G',  'IDLE'),
(154, 1, 'F1-004', '化成柜 F1-004', 6, 'F1-96G',  'IDLE'),
(155, 1, 'F1-005', '化成柜 F1-005', 6, 'F1-96G',  'IDLE'),
(156, 1, 'F1-006', '化成柜 F1-006', 6, 'F1-96G',  'IDLE'),
(157, 1, 'F1-007', '化成柜 F1-007', 6, 'F1-96G',  'IDLE'),
(158, 1, 'F1-008', '化成柜 F1-008', 6, 'F1-96G',  'IDLE'),
(159, 1, 'F1-009', '化成柜 F1-009', 6, 'F1-96G',  'IDLE'),
(160, 1, 'F1-010', '化成柜 F1-010', 6, 'F1-96G',  'IDLE'),
(161, 1, 'F1-011', '化成柜 F1-011', 6, 'F1-96G',  'IDLE'),
(162, 1, 'F1-012', '化成柜 F1-012', 6, 'F1-96G',  'IDLE'),
(171, 1, 'FC-001', '分容柜 FC-001', 7, 'FC-96G',  'IDLE'),
(172, 1, 'FC-002', '分容柜 FC-002', 7, 'FC-96G',  'IDLE'),
(173, 1, 'FC-003', '分容柜 FC-003', 7, 'FC-96G',  'IDLE'),
(201, 2, 'BC-101', '西北涂布机',    11, 'BC-1600', 'IDLE');

-- ---------- 设备组（工序可互替设备） ----------
INSERT INTO resource_group (id, tenant_id, code, name, device_codes) VALUES
(1, 1, 'RG-COAT',  '涂布设备组', '["BC-001","BC-002"]'),
(2, 1, 'RG-ROLL',  '辊压设备组', '["RP-001"]'),
(3, 1, 'RG-SLIT',  '分切设备组', '["SL-001"]'),
(4, 1, 'RG-WIND',  '卷绕设备组', '["WND-001","WND-002","WND-003"]'),
(5, 1, 'RG-FILL',  '注液设备组', '["FL-001"]'),
(6, 1, 'RG-FORM',  '化成设备组', '["F1-001","F1-002","F1-003","F1-004","F1-005","F1-006","F1-007","F1-008","F1-009","F1-010","F1-011","F1-012"]'),
(7, 1, 'RG-GRADE', '分容设备组', '["FC-001","FC-002","FC-003"]'),
(11, 2, 'RG-NW-COAT', '西北涂布设备组', '["BC-101"]');

-- ---------- 设备配方（化成曲线，ch05 capacity_spec 双向校验） ----------
INSERT INTO device_recipe (id, tenant_id, code, capacity_spec, device_models, params) VALUES
(1, 1, 'FORN-280-CHG-V3', '280Ah', '["F1-96G"]',
    '{"cc_current":"0.05C","cv_voltage":"4.20V","cutoff_current":"0.05C"}'),
(2, 1, 'FORN-314-CHG-V2', '314Ah', '["F1-96G"]',
    '{"cc_current":"0.05C","cv_voltage":"4.30V","cutoff_current":"0.05C"}');

-- ---------- Routing（X-280 V3 / X-314 V1） ----------
INSERT INTO routing (id, tenant_id, product_item_id, revision, status) VALUES
(1, 1, 101, 'V3', 'ACTIVE'),
(2, 1, 102, 'V1', 'ACTIVE'),
(3, 2, 201, 'V1', 'ACTIVE');

-- X-280 七段工艺（节拍对齐 ch01 数据表：1000 颗工单工时）
INSERT INTO routing_operation (id, tenant_id, routing_id, seq, operation_code, operation_name,
                               work_center_code, resource_group_id, standard_duration_min,
                               process_params, critical_param_set) VALUES
(11, 1, 1, 1, 'COAT', '涂布', 'WC-COAT',  1, 90,   '{}', 'COAT-THICKNESS-142'),
(12, 1, 1, 2, 'ROLL', '辊压', 'WC-ROLL',  2, 66,   '{}', 'ROLL-THICKNESS-134'),
(13, 1, 1, 3, 'SLIT', '分切', 'WC-SLIT',  3, 24,   '{}', 'SLIT-WIDTH-135'),
(14, 1, 1, 4, 'WIND', '卷绕', 'WC-WIND',  4, 126,  '{}', 'WIND-TENSION-20N'),
(15, 1, 1, 5, 'FILL', '注液', 'WC-FILL',  5, 36,   '{}', 'FILL-QTY-145G'),
(16, 1, 1, 6, 'FORM', '化成', 'WC-FORM',  6, 1440, '{"formation_curve":"FORN-280-CHG-V3"}', 'FORM-CUTOFF-0.05C'),
(17, 1, 1, 7, 'TEST', '分容', 'WC-GRADE', 7, 720,  '{}', 'GRADE-TEMP-25C');

-- X-314 七段工艺（化成曲线不同）
INSERT INTO routing_operation (id, tenant_id, routing_id, seq, operation_code, operation_name,
                               work_center_code, resource_group_id, standard_duration_min,
                               process_params, critical_param_set) VALUES
(21, 1, 2, 1, 'COAT', '涂布', 'WC-COAT',  1, 90,   '{}', 'COAT-THICKNESS-142'),
(22, 1, 2, 2, 'ROLL', '辊压', 'WC-ROLL',  2, 66,   '{}', 'ROLL-THICKNESS-134'),
(23, 1, 2, 3, 'SLIT', '分切', 'WC-SLIT',  3, 24,   '{}', 'SLIT-WIDTH-135'),
(24, 1, 2, 4, 'WIND', '卷绕', 'WC-WIND',  4, 126,  '{}', 'WIND-TENSION-20N'),
(25, 1, 2, 5, 'FILL', '注液', 'WC-FILL',  5, 36,   '{}', 'FILL-QTY-145G'),
(26, 1, 2, 6, 'FORM', '化成', 'WC-FORM',  6, 1440, '{"formation_curve":"FORN-314-CHG-V2"}', 'FORM-CUTOFF-0.05C'),
(27, 1, 2, 7, 'TEST', '分容', 'WC-GRADE', 7, 720,  '{}', 'GRADE-TEMP-25C');

INSERT INTO routing_operation (id, tenant_id, routing_id, seq, operation_code, operation_name,
                               work_center_code, resource_group_id, standard_duration_min,
                               process_params, critical_param_set) VALUES
(31, 2, 3, 1, 'COAT', '涂布', 'WC-NW-COAT', 11, 90, '{}', 'COAT-THICKNESS-142');

-- ---------- BOM（X-280 V5 / X-314 V1，ch05 多层树简化为两层 + 工序绑定） ----------
INSERT INTO bom_header (id, tenant_id, product_item_id, revision, effective_from, status) VALUES
(1, 1, 101, 'V5', DATE '2025-01-01', 'ACTIVE'),
(2, 1, 102, 'V1', DATE '2025-01-01', 'ACTIVE');

INSERT INTO bom_line (id, tenant_id, bom_header_id, line_no, child_item_id, quantity, unit, operation_seq, substitutes) VALUES
(31, 1, 1, 1, 111, 0.85,  'kg',  1, '[{"item_id":112,"priority":1}]'),
(32, 1, 1, 2, 113, 0.55,  'kg',  1, NULL),
(33, 1, 1, 3, 114, 2.60,  'm',   4, NULL),
(34, 1, 1, 4, 115, 0.145, 'kg',  5, NULL),
(35, 1, 1, 5, 116, 1.00,  'pcs', 4, NULL),
(36, 1, 1, 6, 117, 1.00,  'pcs', 4, NULL),
(37, 1, 1, 7, 118, 2.00,  'pcs', 4, NULL);

INSERT INTO bom_line (id, tenant_id, bom_header_id, line_no, child_item_id, quantity, unit, operation_seq, substitutes) VALUES
(41, 1, 2, 1, 112, 0.85,  'kg',  1, '[{"item_id":111,"priority":1}]'),
(42, 1, 2, 2, 121, 0.55,  'kg',  1, NULL),
(43, 1, 2, 3, 122, 2.40,  'm',   4, NULL),
(44, 1, 2, 4, 123, 0.150, 'kg',  5, NULL),
(45, 1, 2, 5, 124, 1.00,  'pcs', 4, NULL),
(46, 1, 2, 6, 125, 1.00,  'pcs', 4, NULL),
(47, 1, 2, 7, 126, 2.00,  'pcs', 4, NULL);

-- ---------- 库存 ----------
INSERT INTO inventory (tenant_id, item_id, quantity) VALUES
(1, 111, 2000), (1, 112, 5000), (1, 113, 5000), (1, 114, 50000),
(1, 115, 2000), (1, 116, 5000), (1, 117, 5000), (1, 118, 20000),
(1, 121, 5000), (1, 122, 50000), (1, 123, 2000), (1, 124, 5000),
(1, 125, 5000), (1, 126, 20000), (1, 130, 500),
(2, 201, 100);

-- ---------- 召回规则元数据（ch06：规则引擎可配置） ----------
INSERT INTO recall_rule_config (tenant_id, rule_name, rule_bean, description) VALUES
(1, 'samePowderLot',       'samePowderLotRecallRule',       '同一正极粉料批次召回（客户合同标准定义）'),
(1, 'sameElectrolyteLot',  'sameElectrolyteLotRecallRule',  '同一电解液批次召回'),
(1, 'sameFormationCurve',  'sameFormationCurveRecallRule',  '同一化成柜+同一化成曲线召回');

-- ---------- 化成容量预测模型版本（ch10：规则版基线） ----------
INSERT INTO model_version (tenant_id, version, model_path, mae, r2, accuracy, false_alarm_rate, miss_rate, status, promoted_at)
VALUES (1, 'v1.0.0-rules', 'rules:baseline-v1', 1.9000, 0.8800, 0.8600, 0.0500, 0.0200, 'PRODUCTION', now());

-- ---------- 序列校正（显式主键种子后推进 identity 序列） ----------
SELECT setval(pg_get_serial_sequence('tenant','id'),          (SELECT MAX(id) FROM tenant));
SELECT setval(pg_get_serial_sequence('item','id'),            (SELECT MAX(id) FROM item));
SELECT setval(pg_get_serial_sequence('work_center','id'),     (SELECT MAX(id) FROM work_center));
SELECT setval(pg_get_serial_sequence('work_cell','id'),       (SELECT MAX(id) FROM work_cell));
SELECT setval(pg_get_serial_sequence('resource_group','id'),  (SELECT MAX(id) FROM resource_group));
SELECT setval(pg_get_serial_sequence('device_recipe','id'),   (SELECT MAX(id) FROM device_recipe));
SELECT setval(pg_get_serial_sequence('routing','id'),         (SELECT MAX(id) FROM routing));
SELECT setval(pg_get_serial_sequence('routing_operation','id'),(SELECT MAX(id) FROM routing_operation));
SELECT setval(pg_get_serial_sequence('bom_header','id'),      (SELECT MAX(id) FROM bom_header));
SELECT setval(pg_get_serial_sequence('bom_line','id'),        (SELECT MAX(id) FROM bom_line));
SELECT setval(pg_get_serial_sequence('inventory','id'),       (SELECT MAX(id) FROM inventory));
SELECT setval(pg_get_serial_sequence('recall_rule_config','id'),(SELECT MAX(id) FROM recall_rule_config));
SELECT setval(pg_get_serial_sequence('model_version','id'),   (SELECT MAX(id) FROM model_version));
