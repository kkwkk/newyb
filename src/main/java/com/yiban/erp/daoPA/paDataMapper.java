package com.yiban.erp.daoPA;


import com.yiban.erp.entitiesPA.TradeDetail;
import com.yiban.erp.entitiesPA.TradeHead;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface paDataMapper {

    public int insertHead(TradeHead tradeHead);

    public void insertDetail(TradeDetail tradeDetail);
}
