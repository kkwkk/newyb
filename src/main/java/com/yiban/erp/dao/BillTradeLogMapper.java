package com.yiban.erp.dao;

import com.yiban.erp.entities.BillTradeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BillTradeLogMapper {
    int deleteByPrimaryKey(Long id);

    int insert(BillTradeLog record);
    List<String> selectAll(@Param(value="begin")Integer begin,
                           @Param(value="end")Integer end);
    int countNum();

}