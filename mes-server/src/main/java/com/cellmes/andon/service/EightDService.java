package com.cellmes.andon.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.andon.AndonLevel;
import com.cellmes.andon.entity.AndonCall;
import com.cellmes.andon.entity.EightDCase;
import com.cellmes.andon.mapper.AndonCallMapper;
import com.cellmes.andon.mapper.EightDCaseMapper;
import com.cellmes.common.domain.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 8D 服务（ch09）：客诉自动开案（D1 团队）→ D2-D8 逐级推进，状态机不可跳步；
 * D3 临时措施支持按批次号自动 hold。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EightDService {

    private static final List<String> STEPS = List.of("D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8");

    private final EightDCaseMapper caseMapper;
    private final AndonCallMapper andonCallMapper;
    private final AndonService andonService;
    private final com.cellmes.trace.service.LotService lotService;
    private final ObjectMapper objectMapper;

    @Data
    public static class StepRequest {
        /** 目标步骤（D2-D8），必须是 currentStep 的下一步 */
        private String step;
        // D2
        private String problemDesc;
        private Map<String, Object> problem5w2h;
        // D3
        private String interimActions;
        /** D3 临时措施：同步 hold 的批次号 */
        private List<String> holdLotNos;
        // D4
        private String rootCause;
        private String rootCauseMethod;
        // D5-D8
        private String permanentActions;
        private String verificationResult;
        private String preventionActions;
        private String recognition;
    }

    /** 客诉入口（ch09）：自动创建 Andon L3 呼叫 + 8D 案例（D1 团队就位）。 */
    @Transactional
    public EightDCase openCustomerComplaint(String title, String description) {
        AndonCall call = andonService.createCall("CUSTOMER", AndonLevel.L3, null, null, null,
                title, description);
        EightDCase eightD = new EightDCase();
        eightD.setTitle(title);
        eightD.setStatus("OPEN");
        eightD.setCurrentStep("D1");
        eightD.setTeamLeadId(9001L);
        try {
            eightD.setTeamMembers(objectMapper.writeValueAsString(List.of(
                    Map.of("user_id", 9001, "role", "质量经理"),
                    Map.of("user_id", 9002, "role", "工艺工程师"),
                    Map.of("user_id", 9003, "role", "生产主管"))));
        } catch (Exception e) {
            throw new BizException(500, "团队成员 JSON 序列化失败");
        }
        eightD.setProblemDesc(description);
        eightD.setAndonCallId(call.getId());
        caseMapper.insert(eightD);

        call.setEightDCaseId(eightD.getId());
        andonCallMapper.updateById(call);
        return eightD;
    }

    /** 步骤推进：状态机不可跳步（D1→D2→…→D8）。 */
    @Transactional
    public EightDCase advance(Long caseId, StepRequest req) {
        EightDCase eightD = get(caseId);
        String expected = nextStep(eightD.getCurrentStep());
        if (req.getStep() == null || !req.getStep().equals(expected)) {
            throw new BizException(409, "8D 不可跳步：当前 " + eightD.getCurrentStep()
                    + "，下一步必须是 " + expected);
        }
        switch (expected) {
            case "D2" -> {
                eightD.setProblemDesc(req.getProblemDesc());
                if (req.getProblem5w2h() != null) {
                    try {
                        eightD.setProblem5w2h(objectMapper.writeValueAsString(req.getProblem5w2h()));
                    } catch (Exception e) {
                        throw new BizException(500, "5W2H JSON 序列化失败");
                    }
                }
            }
            case "D3" -> {
                eightD.setInterimActions(req.getInterimActions());
                eightD.setInterimActionsTime(LocalDateTime.now());
                if (req.getHoldLotNos() != null && !req.getHoldLotNos().isEmpty()) {
                    lotService.holdByLotNos(req.getHoldLotNos());
                }
            }
            case "D4" -> {
                eightD.setRootCause(req.getRootCause());
                eightD.setRootCauseMethod(req.getRootCauseMethod());
            }
            case "D5" -> eightD.setPermanentActions(req.getPermanentActions());
            case "D6" -> eightD.setVerificationResult(req.getVerificationResult());
            case "D7" -> eightD.setPreventionActions(req.getPreventionActions());
            case "D8" -> {
                eightD.setRecognition(req.getRecognition());
                eightD.setStatus("CLOSED");
                eightD.setCloseTime(LocalDateTime.now());
                // 关闭关联 Andon
                AndonCall call = andonCallMapper.selectById(eightD.getAndonCallId());
                if (call != null && !"CLOSED".equals(call.getStatus())) {
                    call.setStatus("CLOSED");
                    call.setClosedAt(LocalDateTime.now());
                    if (call.getResolvedAt() == null) {
                        call.setResolvedAt(call.getClosedAt());
                    }
                    andonCallMapper.updateById(call);
                }
            }
            default -> throw new BizException(409, "未知步骤: " + expected);
        }
        eightD.setCurrentStep(expected);
        caseMapper.updateById(eightD);
        log.info("[8D 推进] case={} → {}", caseId, expected);
        return eightD;
    }

    public EightDCase get(Long caseId) {
        EightDCase eightD = caseMapper.selectById(caseId);
        if (eightD == null) {
            throw new BizException(404, "8D 案例不存在: id=" + caseId);
        }
        return eightD;
    }

    public List<EightDCase> list(String status) {
        return caseMapper.selectList(Wrappers.<EightDCase>lambdaQuery()
                .eq(status != null, EightDCase::getStatus, status)
                .orderByDesc(EightDCase::getOpenTime));
    }

    private String nextStep(String current) {
        int idx = STEPS.indexOf(current);
        if (idx < 0 || idx == STEPS.size() - 1) {
            throw new BizException(409, "8D 已闭环或步骤非法: " + current);
        }
        return STEPS.get(idx + 1);
    }
}
