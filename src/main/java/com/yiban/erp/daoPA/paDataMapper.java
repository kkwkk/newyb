package com.yiban.erp.daoPA;

import com.yiban.erp.entities.TradeLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface paDataMapper {

    public void insertData(TradeLog tradeLogs);
}
