package com.yiban.erp.daoPA;


import com.yiban.erp.entitiesPA.TradeDetail;
import com.yiban.erp.entitiesPA.TradeHead;
import com.yiban.erp.entitiesPA.compInfo;
import com.yiban.erp.entitiesPA.topConsumer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface paDataMapper {

    public int insertHead(TradeHead tradeHead);

    public void insertDetail(TradeDetail tradeDetail);

    public List<compInfo> allComp();
    public List<topConsumer> topConsumer(@Param(value="xfmc") String xfmc);
    public int insertComps(compInfo comp);
    public Long getSellTotal(@Param(value="cropName") String cropName);
    public int selectComp(@Param(value="corpName") String corpName);
    public int updComp(compInfo compInfo);
}
