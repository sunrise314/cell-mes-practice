package com.cellmes.andon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 8D 案例（ch09）：D1-D8 状态机不可跳步；客诉自动开案。 */
@Data
@TableName("eight_d_case")
public class EightDCase {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private LocalDateTime openTime;
    /** OPEN/CLOSED */
    private String status;
    /** D1-D8（状态机不可跳步） */
    private String currentStep;
    private Long teamLeadId;
    /** JSONB：[{"user_id":1,"role":"质量"},...] */
    private String teamMembers;
    private String problemDesc;
    /** JSONB：5W2H */
    private String problem5w2h;
    private String interimActions;
    private LocalDateTime interimActionsTime;
    private String rootCause;
    /** 5WHY / FISHBONE / FMEA */
    private String rootCauseMethod;
    private String permanentActions;
    private String verificationResult;
    private String preventionActions;
    private String recognition;
    private LocalDateTime closeTime;
    private Long andonCallId;
}
