package com.cellmes.andon.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cellmes.andon.entity.AndonCall;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface AndonCallMapper extends BaseMapper<AndonCall> {

    /** 定时升级扫描：跨租户（调度线程无租户上下文），显式豁免拦截器。 */
    @Select("SELECT * FROM andon_call WHERE status = 'OPEN' AND sla_response_deadline < now()")
    @InterceptorIgnore(tenantLine = "true")
    List<AndonCall> selectOverdueOpen();

    /** SLA 减半升级（调度线程无租户上下文，显式豁免 + 显式 SQL）。 */
    @Update("""
            UPDATE andon_call
            SET sla_response_min = #{respMin}, sla_onsite_min = #{onsiteMin},
                sla_response_deadline = #{respDeadline}, sla_onsite_deadline = #{onsiteDeadline},
                escalate_count = #{escalateCount}, escalate_to_id = #{escalateToId}
            WHERE id = #{id}
            """)
    @InterceptorIgnore(tenantLine = "true")
    int escalate(@Param("id") Long id,
                 @Param("respMin") int respMin,
                 @Param("onsiteMin") int onsiteMin,
                 @Param("respDeadline") LocalDateTime respDeadline,
                 @Param("onsiteDeadline") LocalDateTime onsiteDeadline,
                 @Param("escalateCount") int escalateCount,
                 @Param("escalateToId") Long escalateToId);
}
