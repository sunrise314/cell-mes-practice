package com.cellmes.master.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.master.entity.BomHeader;
import com.cellmes.master.entity.BomLine;
import com.cellmes.master.mapper.BomHeaderMapper;
import com.cellmes.master.mapper.BomLineMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** BOM 服务（ch01/ch05）。 */
@Service
@RequiredArgsConstructor
public class BomService {

    private final BomHeaderMapper bomHeaderMapper;
    private final BomLineMapper bomLineMapper;

    public BomHeader getActiveBom(Long productItemId) {
        BomHeader bom = bomHeaderMapper.selectOne(Wrappers.<BomHeader>lambdaQuery()
                .eq(BomHeader::getProductItemId, productItemId)
                .eq(BomHeader::getStatus, "ACTIVE")
                .orderByDesc(BomHeader::getId)
                .last("limit 1"));
        if (bom == null) {
            throw new BizException(404, "产品无 ACTIVE BOM: itemId=" + productItemId);
        }
        return bom;
    }

    public BomHeader getByRevision(Long productItemId, String revision) {
        BomHeader bom = bomHeaderMapper.selectOne(Wrappers.<BomHeader>lambdaQuery()
                .eq(BomHeader::getProductItemId, productItemId)
                .eq(BomHeader::getRevision, revision)
                .last("limit 1"));
        if (bom == null) {
            throw new BizException(404, "BOM 版本不存在: itemId=" + productItemId + ", revision=" + revision);
        }
        return bom;
    }

    public List<BomLine> listLines(Long bomHeaderId) {
        return bomLineMapper.selectList(Wrappers.<BomLine>lambdaQuery()
                .eq(BomLine::getBomHeaderId, bomHeaderId)
                .orderByAsc(BomLine::getLineNo));
    }
}
