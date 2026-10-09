package com.cellmes.trace.dao;

import com.cellmes.common.tenant.TenantContext;
import com.cellmes.trace.entity.Lot;
import com.cellmes.trace.service.LotService;
import com.cellmes.trace.service.RecallService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 追溯 CTE 集成测试（ch06）：真库 + Flyway V1~V10 种子，验证递归 CTE 的
 * 1→N 分叉展开、并行支路（电解液）与召回规则影响面。
 * 需本机 Docker：设置环境变量 MES_IT=true 才执行（默认 build 不依赖 Docker）。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "MES_IT", matches = "true")
class TraceDaoCteIntegrationTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cellmes").withUsername("cellmes").withPassword("cellmes_dev");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // stringtype=unspecified 与生产一致：允许字符串直写 JSONB 列
        registry.add("spring.datasource.url",
                () -> pg.getJdbcUrl() + "?stringtype=unspecified");
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired TraceDao traceDao;
    @Autowired LotService lotService;
    @Autowired RecallService recallService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 正向追溯（追根源）：客诉 SN 回溯应同时命中正极粉料批次与电解液并行支路。 */
    @Test
    void backwardFromCustomerComplaintSn_reachesPowderAndElectrolyte() {
        Lot sn = lotService.getByLotNo("X280A202512150023");
        assertEquals("FINAL_CELL", sn.getLotType());

        List<TraceEdge> edges = traceDao.traceBackward(1L, sn.getId(), 20);
        assertTrue(edges.size() >= 10, "至少覆盖 7 道工序 + 分叉支路，实际 " + edges.size());

        Set<String> fromNos = edges.stream().map(TraceEdge::getFromLotNo).collect(Collectors.toSet());
        assertTrue(fromNos.contains("LFP-PW-20251128-007"), "应追溯到正极粉料源头批次");
        assertTrue(fromNos.contains("EC-20251201-005"), "应覆盖电解液并行投入支路");

        // 递归深度从 1 开始且连续有序
        assertEquals(1, edges.stream().mapToInt(TraceEdge::getDepth).min().orElseThrow());
    }

    /** 反向追溯（追影响面）：从粉料批次向下游扩散，24 颗 SN 全覆盖。 */
    @Test
    void forwardFromPowderLot_coversAll24FinalCells() {
        Lot powder = lotService.getByLotNo("LFP-PW-20251128-007");
        assertEquals("POWDER", powder.getLotType());

        List<TraceEdge> edges = traceDao.traceForward(1L, powder.getId(), 20);
        Set<String> finals = edges.stream()
                .filter(e -> "FINAL_CELL".equals(e.getToLotType()))
                .map(TraceEdge::getToLotNo)
                .collect(Collectors.toSet());
        assertEquals(24, finals.size(), "分切 1→2 小卷 × 各 12 颗 = 24 颗成品");
    }

    /** samePowderLot 召回：24 颗成品全部 HOLD（含已出货批次）。 */
    @Test
    void recallSamePowderLot_holdsAll24FinalCells() {
        RecallService.RecallResult result = recallService.recall("X280A202512150023", "samePowderLot");

        assertEquals("X280A202512150023", result.getSourceLotNo());
        List<String> affected = result.getAffectedByRule().get("samePowderLot");
        assertEquals(24, affected.size());
        assertEquals(24, result.getTotalHeld());

        Lot held = lotService.getByLotNo("X280A202512150023");
        assertEquals("HOLD", held.getStatus());
    }

    /** sameFormationCurve DAO 直测：同化成柜 + 同曲线 → 24 颗成品。 */
    @Test
    void findFinalLotsSameFormationCurve_returnsAll24() {
        Lot sn = lotService.getByLotNo("X280A202512150001");
        Long seq6MovementId = traceDao.traceBackward(1L, sn.getId(), 20).stream()
                .filter(e -> e.getOperationSeq() == 6)
                .findFirst()
                .orElseThrow()
                .getMovementId();

        List<Lot> finals = traceDao.findFinalLotsSameFormationCurve(1L, seq6MovementId);
        assertEquals(24, finals.stream().map(Lot::getLotNo).distinct().count());
    }
}
