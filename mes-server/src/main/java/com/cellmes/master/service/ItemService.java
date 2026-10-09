package com.cellmes.master.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.master.entity.Item;
import com.cellmes.master.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemMapper itemMapper;

    public Item getByCode(String code) {
        Item item = itemMapper.selectOne(Wrappers.<Item>lambdaQuery().eq(Item::getCode, code).last("limit 1"));
        if (item == null) {
            throw new BizException(404, "物料不存在: " + code);
        }
        return item;
    }

    public List<Item> list(String itemType) {
        return itemMapper.selectList(Wrappers.<Item>lambdaQuery()
                .eq(itemType != null, Item::getItemType, itemType)
                .orderByAsc(Item::getCode));
    }
}
