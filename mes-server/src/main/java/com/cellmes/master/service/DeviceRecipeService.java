package com.cellmes.master.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.master.entity.DeviceRecipe;
import com.cellmes.master.mapper.DeviceRecipeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 设备配方服务（ch05：化成曲线等）。 */
@Service
@RequiredArgsConstructor
public class DeviceRecipeService {

    private final DeviceRecipeMapper deviceRecipeMapper;

    public DeviceRecipe getByCode(String code) {
        DeviceRecipe recipe = deviceRecipeMapper.selectOne(Wrappers.<DeviceRecipe>lambdaQuery()
                .eq(DeviceRecipe::getCode, code)
                .last("limit 1"));
        if (recipe == null) {
            throw new BizException(404, "设备配方不存在: " + code);
        }
        return recipe;
    }
}
