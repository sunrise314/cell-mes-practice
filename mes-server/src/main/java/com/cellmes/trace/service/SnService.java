package com.cellmes.trace.service;

import com.cellmes.master.entity.Item;
import com.cellmes.master.mapper.ItemMapper;
import com.cellmes.trace.entity.Lot;
import com.cellmes.trace.mapper.LotMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** SN 生成（ch06）：型号前缀 + 日期 + 4 位流水，SN 贯穿注液后全阶段不变。 */
@Service
@RequiredArgsConstructor
public class SnService {

    private static final DateTimeFormatter SN_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LotMapper lotMapper;
    private final ItemMapper itemMapper;

    /** X-280 + 2025-12-15 + 1 → X280A202512150001 */
    public String generateSn(Long productId) {
        Item item = itemMapper.selectById(productId);
        if (item == null) {
            throw new IllegalArgumentException("产品物料不存在: " + productId);
        }
        String prefix = item.getCode().replace("-", "") + "A";
        String datePart = LocalDate.now().format(SN_DATE);
        String like = prefix + datePart + "%";
        Long count = lotMapper.selectCount(Wrappers.<Lot>lambdaQuery().likeRight(Lot::getLotNo, like));
        return prefix + datePart + String.format("%04d", count + 1);
    }
}
