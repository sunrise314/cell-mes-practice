package com.cellmes.master.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cellmes.master.entity.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 条件扣减（ch05）：数量不足返回 0 行，业务层据此抛异常。
     * 行级租户条件由拦截器自动追加。
     */
    @Update("UPDATE inventory SET quantity = quantity - #{qty} WHERE item_id = #{itemId} AND quantity >= #{qty}")
    int deduct(@Param("itemId") Long itemId, @Param("qty") java.math.BigDecimal qty);
}
