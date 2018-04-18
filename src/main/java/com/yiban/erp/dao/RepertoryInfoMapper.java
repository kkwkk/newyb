package com.yiban.erp.dao;

import com.yiban.erp.dto.CurrentBalanceResp;
import com.yiban.erp.entities.RepertoryInfo;
import com.yiban.erp.dto.RepertoryQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface RepertoryInfoMapper {
    int deleteByPrimaryKey(Long id);

    int insert(RepertoryInfo record);

    int insertBatch(@Param("list") List<RepertoryInfo> infoList);

    int insertSelective(RepertoryInfo record);

    RepertoryInfo selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(RepertoryInfo record);

    int updateByPrimaryKey(RepertoryInfo record);

    Integer getDetailListCount(Map<String, Object> paramMap);

    List<RepertoryInfo> getDetailList(Map<String, Object> paramMap);

    List<RepertoryInfo> getListByIdList(@Param("idList") List<Long> repertoryIdList);

    List<String> getGoodNameWithLessQuantity(Long sellOrderId);

    Integer  queryRepertoryCount(RepertoryQuery repertoryQuery);

    List<RepertoryInfo> queryRepertoryPage(RepertoryQuery repertoryQuery);

    //减库存
    int sellOrderConsumeQuantity(@Param("sellOrderId") Long sellOrderId,
                                 @Param("updateBy") String updateBy,
                                 @Param("updateTime") Date updateTime);

    //获取当前仓库中某一类商品的存量
    List<CurrentBalanceResp> getBalance(@Param("warehouseId") Integer warehouseId, @Param("goodsIdList") List<Long> goodsIdList);
    //获取某一商品最近一次的采购价
    List<CurrentBalanceResp> getLastBuyPrice(@Param("warehouseId") Integer warehouseId, @Param("goodsIdList") List<Long> goodsIdList);

    List<RepertoryInfo> selectByIdList(@Param("idList") List<Long> idList);

}