package com.cellmes.master.controller;

import com.cellmes.common.api.R;
import com.cellmes.master.entity.DeviceRecipe;
import com.cellmes.master.entity.Item;
import com.cellmes.master.entity.ResourceGroup;
import com.cellmes.master.entity.WorkCell;
import com.cellmes.master.mapper.DeviceRecipeMapper;
import com.cellmes.master.mapper.ResourceGroupMapper;
import com.cellmes.master.mapper.WorkCellMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.master.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 主数据查询（ch01）：物料 / 工作单元 / 设备组 / 设备配方。 */
@RestController
@RequestMapping("/api/master")
@RequiredArgsConstructor
public class MasterController {

    private final ItemService itemService;
    private final WorkCellMapper workCellMapper;
    private final ResourceGroupMapper resourceGroupMapper;
    private final DeviceRecipeMapper deviceRecipeMapper;

    @GetMapping("/items")
    public R<List<Item>> items(@RequestParam(required = false) String itemType) {
        return R.ok(itemService.list(itemType));
    }

    @GetMapping("/work-cells")
    public R<List<WorkCell>> workCells() {
        return R.ok(workCellMapper.selectList(Wrappers.<WorkCell>lambdaQuery().orderByAsc(WorkCell::getCode)));
    }

    @GetMapping("/resource-groups")
    public R<List<ResourceGroup>> resourceGroups() {
        return R.ok(resourceGroupMapper.selectList(Wrappers.emptyWrapper()));
    }

    @GetMapping("/device-recipes")
    public R<List<DeviceRecipe>> deviceRecipes() {
        return R.ok(deviceRecipeMapper.selectList(Wrappers.emptyWrapper()));
    }
}
