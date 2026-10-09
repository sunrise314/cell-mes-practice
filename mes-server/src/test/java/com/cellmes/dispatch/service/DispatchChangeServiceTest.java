package com.cellmes.dispatch.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cellmes.common.domain.BizException;
import com.cellmes.dispatch.dto.DispatchChangeRequest;
import com.cellmes.dispatch.dto.DispatchChangeResult;
import com.cellmes.master.entity.*;
import com.cellmes.master.mapper.*;
import com.cellmes.master.service.BomService;
import com.cellmes.master.service.RoutingService;
import com.cellmes.master.service.ScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 改派四步法 7 项前置校验（ch05）。
 * 单测用 Mockito 纯桩：不连库，重点验证校验逻辑与 fail-closed 行为。
 */
class DispatchChangeServiceTest {

    private WorkOrderMapper woMapper;
    private WorkOrderOperationMapper wooMapper;
    private WorkOrderBomLineMapper wobMapper;
    private ItemMapper itemMapper;
    private RoutingService routingService;
    private BomService bomService;
    private RoutingOperationMapper roMapper;
    private BomLineMapper blMapper;
    private InventoryMapper invMapper;
    private DeviceRecipeMapper drMapper;
    private ResourceGroupMapper rgMapper;
    private WorkCellMapper wcMapper;
    private ScheduleService scheduleService;
    private FormationCurveDispatcher curveDispatcher;
    private ApplicationEventPublisher publisher;
    private DispatchChangeService service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 需要实体的 TableInfo 缓存（无 Spring 上下文时手工注册）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(WorkOrder.class, WorkOrderOperation.class, WorkOrderBomLine.class,
                Item.class, Routing.class, RoutingOperation.class, BomHeader.class, BomLine.class,
                Inventory.class, DeviceRecipe.class, ResourceGroup.class, WorkCell.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        woMapper = mock(WorkOrderMapper.class);
        wooMapper = mock(WorkOrderOperationMapper.class);
        wobMapper = mock(WorkOrderBomLineMapper.class);
        itemMapper = mock(ItemMapper.class);
        routingService = mock(RoutingService.class);
        bomService = mock(BomService.class);
        roMapper = mock(RoutingOperationMapper.class);
        blMapper = mock(BomLineMapper.class);
        invMapper = mock(InventoryMapper.class);
        drMapper = mock(DeviceRecipeMapper.class);
        rgMapper = mock(ResourceGroupMapper.class);
        wcMapper = mock(WorkCellMapper.class);
        scheduleService = mock(ScheduleService.class);
        curveDispatcher = mock(FormationCurveDispatcher.class);
        publisher = mock(ApplicationEventPublisher.class);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new DispatchChangeService(woMapper, wooMapper, wobMapper, itemMapper, routingService,
                bomService, roMapper, blMapper, invMapper, drMapper, rgMapper, wcMapper,
                scheduleService, curveDispatcher, publisher, objectMapper);
        stubHappyPath();
    }

    // ---------- 桩：对齐种子数据 WO-20251204-001（X-314 → 改派到 X-280 三件套） ----------
    private void stubHappyPath() {
        WorkOrder wo = new WorkOrder();
        wo.setId(9002L);
        wo.setWoNo("WO-20251204-001");
        wo.setProductItemId(102L);
        wo.setQuantity(1000);
        wo.setRoutingId(2L);
        wo.setBomHeaderId(2L);
        wo.setTargetDeviceCode("F1-012");
        wo.setStatus("RELEASED");
        when(woMapper.selectOne(any())).thenReturn(wo);

        String[] statuses = {"WAITING", "WAITING", "WAITING", "WAITING",
                "WAITING", "WAITING", "WAITING"};
        var ops = new java.util.ArrayList<WorkOrderOperation>();
        for (int seq = 1; seq <= 7; seq++) {
            WorkOrderOperation op = new WorkOrderOperation();
            op.setSeq(seq);
            op.setParamSetVersion(1);
            op.setStatus(statuses[seq - 1]);
            ops.add(op);
        }
        when(wooMapper.selectList(any())).thenReturn(ops);

        when(itemMapper.selectById(101L)).thenReturn(item(101L, "X-280", "280Ah"));
        when(itemMapper.selectById(102L)).thenReturn(item(102L, "X-314", "314Ah"));
        when(itemMapper.selectById(999L)).thenReturn(item(999L, "X-XXX", "999Ah"));

        Routing routing = new Routing();
        routing.setId(1L);
        routing.setProductItemId(101L);
        routing.setRevision("V3");
        routing.setStatus("ACTIVE");
        when(routingService.getActiveRouting(anyLong())).thenReturn(routing);
        when(routingService.listOperations(anyLong())).thenReturn(templates());

        BomHeader bom = new BomHeader();
        bom.setId(1L);
        bom.setProductItemId(101L);
        bom.setRevision("V5");
        bom.setStatus("ACTIVE");
        when(bomService.getActiveBom(anyLong())).thenReturn(bom);
        when(bomService.listLines(anyLong())).thenReturn(List.of(line(1L, 111L, "0.85"),
                line(2L, 113L, "0.55")));

        WorkCell cell = new WorkCell();
        cell.setCode("F1-012");
        cell.setModel("F1-96G");
        when(wcMapper.selectOne(any())).thenReturn(cell);

        ResourceGroup group = new ResourceGroup();
        group.setCode("RG-FORM");
        group.setDeviceCodes("[\"F1-001\",\"F1-012\"]");
        when(rgMapper.selectById(6L)).thenReturn(group);

        DeviceRecipe recipe = new DeviceRecipe();
        recipe.setCode("FORN-280-CHG-V3");
        recipe.setCapacitySpec("280Ah");
        when(drMapper.selectOne(any())).thenReturn(recipe);

        Inventory inventory = new Inventory();
        inventory.setItemId(111L);
        inventory.setQuantity(new BigDecimal("100000"));
        when(invMapper.selectOne(any())).thenReturn(inventory);

        var expanded = new java.util.ArrayList<WorkOrderOperation>();
        for (int seq = 1; seq <= 7; seq++) {
            WorkOrderOperation op = new WorkOrderOperation();
            op.setSeq(seq);
            op.setWorkCellCode(seq == 6 ? "F1-012" : "WC-" + seq);
            expanded.add(op);
        }
        when(scheduleService.expand(any(), any(), anyInt(), any(), any())).thenReturn(expanded);
    }

    private Item item(long id, String code, String capacitySpec) {
        Item item = new Item();
        item.setId(id);
        item.setCode(code);
        item.setCapacitySpec(capacitySpec);
        item.setItemType("PRODUCT");
        return item;
    }

    private List<RoutingOperation> templates() {
        var list = new java.util.ArrayList<RoutingOperation>();
        for (int seq = 1; seq <= 7; seq++) {
            RoutingOperation tpl = new RoutingOperation();
            tpl.setSeq(seq);
            tpl.setOperationCode(switch (seq) {
                case 1 -> "COAT";
                case 2 -> "ROLL";
                case 3 -> "SLIT";
                case 4 -> "WIND";
                case 5 -> "FILL";
                case 6 -> "FORM";
                default -> "TEST";
            });
            tpl.setStandardDurationMin(BigDecimal.valueOf(90));
            tpl.setResourceGroupId(seq == 6 ? 6L : (long) seq);
            if (seq == 6) {
                tpl.setProcessParams("{\"formation_curve\":\"FORN-280-CHG-V3\"}");
                tpl.setCriticalParamSet("FORM-CUTOFF-0.05C");
            }
            list.add(tpl);
        }
        return list;
    }

    private BomLine line(long lineNo, long childItemId, String quantity) {
        BomLine line = new BomLine();
        line.setLineNo((int) lineNo);
        line.setChildItemId(childItemId);
        line.setQuantity(new BigDecimal(quantity));
        line.setOperationSeq(1);
        return line;
    }

    private DispatchChangeRequest request(Long newProductId) {
        DispatchChangeRequest req = new DispatchChangeRequest();
        req.setWoNo("WO-20251204-001");
        req.setNewProductItemId(newProductId);
        return req;
    }

    @Test
    @DisplayName("已发料拦截：seq1=ISSUED 的工单改派直接 fail-closed，不产生任何写操作")
    void issuedOperationBlocksChange() {
        var ops = new java.util.ArrayList<WorkOrderOperation>();
        for (int seq = 1; seq <= 7; seq++) {
            WorkOrderOperation op = new WorkOrderOperation();
            op.setSeq(seq);
            op.setParamSetVersion(1);
            op.setStatus(seq == 1 ? "ISSUED" : "WAITING");
            ops.add(op);
        }
        when(wooMapper.selectList(any())).thenReturn(ops);

        assertThatThrownBy(() -> service.change(request(101L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已发料");
        verify(wooMapper, never()).delete(any());
        verify(wooMapper, never()).insert(any(WorkOrderOperation.class));
        verify(woMapper, never()).updateById(any(WorkOrder.class));
    }

    @Test
    @DisplayName("化成曲线一致性拦截：曲线 capacity_spec 与产品不一致 → 409")
    void formationCurveMismatchBlocksChange() {
        assertThatThrownBy(() -> service.change(request(999L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("化成曲线一致性");
        verify(wobMapper, never()).delete(any());
    }

    @Test
    @DisplayName("改派通过：X-314 → X-280，删旧重插派工单与 BOM 行，参数集版本 +1")
    void happyPathReExpands() {
        DispatchChangeResult result = service.change(request(101L));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getChecks()).hasSize(7);
        assertThat(result.getChecks()).allMatch(DispatchChangeResult.Check::isPass);
        assertThat(result.getSummary().getParamSetVersion()).isEqualTo(2);
        assertThat(result.getSummary().getTargetDevice()).isEqualTo("F1-012");

        verify(wobMapper).delete(any());
        verify(wobMapper, times(2)).insert(any(WorkOrderBomLine.class));
        verify(wooMapper).delete(any());
        verify(wooMapper, times(7)).insert(any(WorkOrderOperation.class));
        verify(woMapper).updateById(any(WorkOrder.class));
        verify(curveDispatcher).dispatch(any());
    }

    @Test
    @DisplayName("dryRun 试算：返回完整检查清单且不落库")
    void dryRunDoesNotPersist() {
        DispatchChangeRequest req = request(101L);
        req.setDryRun(true);
        DispatchChangeResult result = service.change(req);
        assertThat(result.isDryRun()).isTrue();
        assertThat(result.isPassed()).isTrue();
        verify(wooMapper, never()).delete(any());
        verify(wobMapper, never()).delete(any());
    }

    @Test
    @DisplayName("库存不足拦截：需求超出库存 → 409")
    void insufficientInventoryBlocksChange() {
        Inventory low = new Inventory();
        low.setItemId(111L);
        low.setQuantity(new BigDecimal("100"));
        when(invMapper.selectOne(any())).thenReturn(low);

        assertThatThrownBy(() -> service.change(request(101L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存充足性");
    }
}
