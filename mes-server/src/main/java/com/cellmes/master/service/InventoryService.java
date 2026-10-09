package com.cellmes.master.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.master.entity.Inventory;
import com.cellmes.master.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 库存服务（ch05 改派校验 1.6 / 发料扣减）。 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryMapper inventoryMapper;

    public Inventory getByItemId(Long itemId) {
        return inventoryMapper.selectOne(Wrappers.<Inventory>lambdaQuery()
                .eq(Inventory::getItemId, itemId)
                .last("limit 1"));
    }

    /** 库存充足性校验（只读，不改账）。 */
    public void checkEnough(Long itemId, BigDecimal required) {
        Inventory inv = getByItemId(itemId);
        if (inv == null || inv.getQuantity().compareTo(required) < 0) {
            throw new BizException(409, "库存不足: itemId=" + itemId
                    + ", 需求=" + required
                    + ", 现有=" + (inv == null ? "0" : inv.getQuantity()));
        }
    }

    /** 条件扣减：UPDATE ... WHERE quantity >= ? 影响行数为 0 即失败（并发安全）。 */
    public void deduct(Long itemId, BigDecimal qty) {
        int rows = inventoryMapper.deduct(itemId, qty);
        if (rows == 0) {
            throw new BizException(409, "库存扣减失败（并发不足）: itemId=" + itemId);
        }
    }
}
