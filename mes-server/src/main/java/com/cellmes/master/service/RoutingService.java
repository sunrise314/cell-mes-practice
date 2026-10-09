package com.cellmes.master.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.master.entity.Routing;
import com.cellmes.master.entity.RoutingOperation;
import com.cellmes.master.mapper.RoutingMapper;
import com.cellmes.master.mapper.RoutingOperationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 工艺路线服务（ch01 三件套模板层）。 */
@Service
@RequiredArgsConstructor
public class RoutingService {

    private final RoutingMapper routingMapper;
    private final RoutingOperationMapper routingOperationMapper;

    /** 取产品当前 ACTIVE 工艺路线（多版本时取最新 ID）。 */
    public Routing getActiveRouting(Long productItemId) {
        Routing routing = routingMapper.selectOne(Wrappers.<Routing>lambdaQuery()
                .eq(Routing::getProductItemId, productItemId)
                .eq(Routing::getStatus, "ACTIVE")
                .orderByDesc(Routing::getId)
                .last("limit 1"));
        if (routing == null) {
            throw new BizException(404, "产品无 ACTIVE 工艺路线: itemId=" + productItemId);
        }
        return routing;
    }

    public Routing getByRevision(Long productItemId, String revision) {
        Routing routing = routingMapper.selectOne(Wrappers.<Routing>lambdaQuery()
                .eq(Routing::getProductItemId, productItemId)
                .eq(Routing::getRevision, revision)
                .last("limit 1"));
        if (routing == null) {
            throw new BizException(404, "工艺路线版本不存在: itemId=" + productItemId + ", revision=" + revision);
        }
        return routing;
    }

    /** 工序列表，按 seq 升序（7 段工艺 DAG 的拓扑近似）。 */
    public List<RoutingOperation> listOperations(Long routingId) {
        return routingOperationMapper.selectList(Wrappers.<RoutingOperation>lambdaQuery()
                .eq(RoutingOperation::getRoutingId, routingId)
                .orderByAsc(RoutingOperation::getSeq));
    }
}
