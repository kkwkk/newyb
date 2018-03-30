package com.yiban.erp.dao;

import com.yiban.erp.entities.RepertoryInfo;
import com.yiban.erp.querybean.RepertoryQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface RepertoryInfoMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(RepertoryInfo record);

    int insertSelective(RepertoryInfo record);

    RepertoryInfo selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(RepertoryInfo record);

    int updateByPrimaryKey(RepertoryInfo record);

    Integer getDetailListCount(Map<String, Object> paramMap);

    List<RepertoryInfo> getDetailList(Map<String, Object> paramMap);

    List<RepertoryInfo> getListByIdList(@Param("idList") List<Long> repertoryIdList);

    List<String> getGoodNameWithLessQuantity(Long sellOrderId);

    List<RepertoryInfo> queryRepertory(RepertoryQuery repertoryQuery);



    //减库存
    int sellOrderConsumeQuantity(@Param("sellOrderId") Long sellOrderId,
                                 @Param("updateBy") String updateBy,
                                 @Param("updateTime") Date updateTime);

    //获取当前仓库中某一类商品的存量
    Integer getBalance(@Param("warehouseId") Integer warehouseId, @Param("goodsId") Long goodsId);
    //获取某一商品最近一次的采购价
    BigDecimal getLastBuyPrice(@Param("warehouseId") Integer warehouseId, @Param("goodsId") Long goodsId);
}