package com.yiban.erp.service.sell;

import com.sun.org.apache.bcel.internal.generic.IF_ACMPEQ;
import com.sun.org.apache.regexp.internal.RE;
import com.yiban.erp.config.RabbitmqQueueConfig;
import com.yiban.erp.constant.OrderNumberType;
import com.yiban.erp.constant.RepertoryRefType;
import com.yiban.erp.constant.SellBackStatus;
import com.yiban.erp.constant.SellOrderStatus;
import com.yiban.erp.dao.RepertoryInfoMapper;
import com.yiban.erp.dao.SellOrderBackDetailMapper;
import com.yiban.erp.dao.SellOrderBackMapper;
import com.yiban.erp.dao.SellOrderMapper;
import com.yiban.erp.dto.SellBackCheck;
import com.yiban.erp.dto.SellBackQuery;
import com.yiban.erp.entities.*;
import com.yiban.erp.exception.BizException;
import com.yiban.erp.exception.BizRuntimeException;
import com.yiban.erp.exception.ErrorCode;
import com.yiban.erp.service.GoodsService;
import com.yiban.erp.util.UtilTool;
import org.omg.CORBA.ORB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sun.rmi.runtime.Log;

import java.math.BigDecimal;
import java.util.*;

@Service
public class SellOrderBackService {

    private static final Logger logger = LoggerFactory.getLogger(SellOrderBackService.class);

    @Autowired
    private SellOrderBackMapper sellOrderBackMapper;
    @Autowired
    private SellOrderBackDetailMapper sellOrderBackDetailMapper;
    @Autowired
    private SellOrderMapper sellOrderMapper;
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private RepertoryInfoMapper repertoryInfoMapper;
    @Autowired
    private RabbitmqQueueConfig rabbitmqQueueConfig;


    private void validateAddOrder(SellOrderBack back) throws BizException {
        if (back.getCustomerId() == null) {
            logger.warn("params customerId is null.");
            throw new BizException(ErrorCode.PARAMETER_MISSING);
        }
        if (back.getSaleId() == null) {
            logger.warn("params saleId is null");
            throw new BizException(ErrorCode.PARAMETER_MISSING);
        }
        if (back.getWarehouseId() == null) {
            logger.warn("params warehouse id is null");
            throw new BizException(ErrorCode.PARAMETER_MISSING);
        }
        if (back.getFreeAmount() != null && BigDecimal.ZERO.compareTo(back.getFreeAmount()) < 0) {
            logger.warn("params costAmount must <= 0");
            throw new BizException(ErrorCode.SELL_BACK_COST_AMOUNT_ERROR);
        }
        List<SellOrderBackDetail> details = back.getDetails();
        if (details == null || details.isEmpty()) {
            logger.warn("params details is empty.");
            throw new BizException(ErrorCode.SELL_BACK_ADD_DETAIL_EMPTY);
        }
    }

    @Transactional
    public void add(SellOrderBack orderBack, User user) throws BizException {
        if (orderBack ==null) {
            throw new BizException(ErrorCode.PARAMETER_MISSING);
        }
        validateAddOrder(orderBack);
        //获取详情退货数量大于0的产品信息
        List<SellOrderBackDetail> details = new ArrayList<>();
        orderBack.getDetails()
            .stream().forEach(item -> {
                if (item.getBackQuantity() != null && item.getBackQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    details.add(item);
                }
            });
        if (details.isEmpty()) {
            logger.warn("params add detail back quantity <= 0");
            throw new BizException(ErrorCode.SELL_BACK_ADD_DETAIL_QUANTITY_ERROR);
        }
        SellOrder sellOrder = null;
        if (orderBack.getHaveSellOrder()) {
            sellOrder = sellOrderMapper.selectByPrimaryKey(orderBack.getRefOrderId());
            if (sellOrder == null || !SellOrderStatus.SALE_CHECKED.name().equalsIgnoreCase(sellOrder.getStatus())) {
                throw new BizException(ErrorCode.SELL_BACK_GET_OUT_RECORD_FAIL);
            }
            //校验退货数量是否超出订单销售数量
        }
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCostAmount = BigDecimal.ZERO;
        for (SellOrderBackDetail detail : details) {
            totalQuantity = totalQuantity.add(detail.getBackQuantity());
            totalAmount = totalAmount.add(detail.getAmount() == null ? BigDecimal.ZERO : detail.getAmount());
            totalCostAmount = totalCostAmount.add(detail.getCostAmount() == null ? BigDecimal.ZERO : detail.getCostAmount());
        }

        //创建一个销售退货单信息
        orderBack.setCompanyId(user.getCompanyId());
        orderBack.setOrderNumber(UtilTool.makeOrderNumber(user.getCompanyId(), OrderNumberType.SELL_BACK));
        orderBack.setStatus(SellBackStatus.INIT.name());
        if (orderBack.getHaveSellOrder()) {
            orderBack.setRefOrderId(sellOrder.getId());
            orderBack.setRefOrderNo(sellOrder.getOrderNumber());
        }
        orderBack.setTotalAmount(totalAmount);
        orderBack.setTotalQuantity(totalQuantity);
        orderBack.setTotalCostAmount(totalCostAmount);
        orderBack.setCreatedBy(user.getNickname());
        orderBack.setCreatedTime(new Date());
        int count = sellOrderBackMapper.insert(orderBack);
        if (count <=0 || orderBack.getId() == null) {
            throw new BizRuntimeException(ErrorCode.FAILED_INSERT_FROM_DB);
        }
        logger.info("sell back order insert success id:{}", orderBack.getId());

        for (SellOrderBackDetail detail : details) {
            detail.setBackOrderId(orderBack.getId());
            detail.setRightQuantity(detail.getBackQuantity()); //初始设置为全部正确
            detail.setBadQuantity(BigDecimal.ZERO);
            detail.setCreatedBy(user.getNickname());
            detail.setCreatedTime(new Date());
        }

        //保存详情信息
        int detailCount = sellOrderBackDetailMapper.insertBatch(details);
        if (detailCount <= 0) {
            throw new BizRuntimeException(ErrorCode.FAILED_INSERT_FROM_DB);
        }
        logger.info("sell back order detail insert success. detailCount:{}", detailCount);

        if (orderBack.getHaveSellOrder()) {
            //修改订单详情的退货数量
            sellOrderBackDetailMapper.updateAlreadyBackQuantity(orderBack.getId());
        }
    }

    public List<SellOrderBack> getOrderList(SellBackQuery query) {
        if (query == null || query.getCompanyId() == null) {
            return Collections.emptyList();
        }
        return sellOrderBackMapper.getOrderList(query);
    }

    public List<SellOrderBackDetail> getDetails(Long backOrderId) {
        List<SellOrderBackDetail> details = sellOrderBackDetailMapper.getByBackOrderId(backOrderId);
        if (details.isEmpty()) {
            return details;
        }
        List<Long> goodsIds = new ArrayList<>();
        details.stream().forEach(item -> goodsIds.add(item.getGoodsId()));
        List<Goods> goodsList = goodsService.getGoodsById(goodsIds);
        Map<Long, Goods> goodsMap = new HashMap<>();
        goodsList.stream().forEach(item -> goodsMap.put(item.getId(), item));
        details.stream().forEach(item -> item.setGoods(goodsMap.get(item.getGoodsId())));
        return details;
    }

    public void checkBackOrder(SellBackCheck backCheck, User user) throws BizException {
        //检查验请求的状态是否正确
        SellBackStatus requestStatus = SellBackStatus.getByName(backCheck.getStatus());
        if (requestStatus == null) {
            logger.warn("get sell back status fail by status:{}", backCheck.getStatus());
            throw new BizException(ErrorCode.SELL_BACK_CHECK_STATUS_ERROR);
        }
        switch (requestStatus) {
            case INIT_SALE_CHECKED:
                initSaleCheck(backCheck, user);//初始销售经理审核
                return;
            case INIT_QUALITY_CHECKED:
                initQualityCheck(backCheck, user);//初始质量经理审核
                return;
            case QUALITY_CHECKED:
                qualityCheck(backCheck, user);
                return;
            case FINAL_CHECKED:
                finalCheck(backCheck, user);
                return;
        }
        logger.warn("get request status error. requestStatus:{}", requestStatus.name());
        throw new BizException(ErrorCode.SELL_BACK_CHECK_STATUS_ERROR);
    }

    /**
     * 销售经理初审
     * @param check
     * @param user
     */
    @Transactional
    public void initSaleCheck(SellBackCheck check, User user) throws BizException {
        SellOrderBack order = sellOrderBackMapper.selectByPrimaryKey(check.getBackOrderId());
        if (order == null || !user.getCompanyId().equals(order.getCompanyId())) {
            logger.warn("get sell order back fail or user company and order company is not match. orderId:{}", check.getBackOrderId());
            throw new BizException(ErrorCode.SELL_BACK_ORDER_GET_FAIL);
        }
        if (!SellBackStatus.INIT.name().equalsIgnoreCase(order.getStatus())) {
            logger.warn("order:{} is not in INIT status, check cannot do. user:{}", order.getId(), user.getId());
            throw new BizException(ErrorCode.SELL_BACK_INIT_SALE_CHECK_ERROR);
        }
        //如果状态正确，直接修改状态和登记审核信息
        order.setStatus(SellBackStatus.INIT_SALE_CHECKED.name());
        order.setBackSaleUser(user.getNickname());
        order.setBackSaleTime(new Date());
        order.setBackSaleResult(check.getCheckResult());
        order.setUpdatedBy(user.getNickname());
        order.setUpdatedTime(new Date());
        sellOrderBackMapper.updateByPrimaryKeySelective(order);
    }

    /**
     * 质量经理初审
     * @param check
     * @param user
     */
    @Transactional
    public void initQualityCheck(SellBackCheck check, User user) throws BizException {
        SellOrderBack order = sellOrderBackMapper.selectByPrimaryKey(check.getBackOrderId());
        if (order == null || !user.getCompanyId().equals(order.getCompanyId())) {
            logger.warn("get sell order back fail or user company and order company is not match. orderId:{}", check.getBackOrderId());
            throw new BizException(ErrorCode.SELL_BACK_ORDER_GET_FAIL);
        }
        if (!SellBackStatus.INIT_SALE_CHECKED.name().equalsIgnoreCase(order.getStatus())) {
            logger.warn("order:{} is not in INIT_SALE_CHECKED status, check cannot do. user:{}", order.getId(), user.getId());
            throw new BizException(ErrorCode.SELL_BACK_INIT_QUALITY_CHECK_ERROR);
        }
        //如果状态正确，直接修改状态和登记审核信息
        order.setStatus(SellBackStatus.INIT_QUALITY_CHECKED.name());
        order.setBackQualityUser(user.getNickname());
        order.setBackQualityTime(new Date());
        order.setBackQualityResult(check.getCheckResult());
        order.setUpdatedBy(user.getNickname());
        order.setUpdatedTime(new Date());
        sellOrderBackMapper.updateByPrimaryKeySelective(order);
    }

    /**
     * 质量复核
     * @param check
     * @param user
     * @throws BizException
     */
    public void qualityCheck(SellBackCheck check, User user) throws BizException {
        SellOrderBack order = sellOrderBackMapper.selectByPrimaryKey(check.getBackOrderId());
        if (order == null || !user.getCompanyId().equals(order.getCompanyId())) {
            logger.warn("get sell order back fail or user company and order company is not match. orderId:{}", check.getBackOrderId());
            throw new BizException(ErrorCode.SELL_BACK_ORDER_GET_FAIL);
        }
        if (!SellBackStatus.BACK_RECEIVE.name().equalsIgnoreCase(order.getStatus()) &&
                !SellBackStatus.QUALITY_CHECKED.name().equalsIgnoreCase(order.getStatus())) {
            logger.warn("order:{} is not in BACK_RECEIVE or QUALITY_CHECKED status, check cannot do. user:{}", order.getId(), user.getId());
            throw new BizException(ErrorCode.SELL_BACK_QUALITY_CHECK_ERROR);
        }
        //看是否存在有detailId, 如果存在，认为是验收一条详情的数据
        if (check.getDetailId() != null) {
            qualityCheckDetail(order, check, user);
        }else {
            qualityCheckOrder(order, check, user);
        }
    }

    @Transactional
    public void qualityCheckDetail(SellOrderBack order, SellBackCheck check, User user) throws BizException {
        if (check.getRightQuantity() == null || check.getRightQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.SELL_BACK_RIGHT_QUANTITY_ERROR);
        }
        SellOrderBackDetail detail = sellOrderBackDetailMapper.selectByPrimaryKey(check.getDetailId());
        if (detail == null) {
            throw new BizException(ErrorCode.SELL_BACK_DETAIL_GET_FAIL);
        }
        if (check.getRightQuantity().compareTo(detail.getBackQuantity()) > 0) {
            throw new BizException(ErrorCode.SELL_BACK_RIGHT_QUANTITY_ERROR);
        }
        check.setCheckStatus(true);
        check.setCheckUser(user.getNickname());
        check.setUpdatedBy(user.getNickname());
        check.setUpdatedTime(new Date());
        int count = sellOrderBackDetailMapper.updateCheckStatus(check);
        if (count <= 0) {
            throw new BizRuntimeException(ErrorCode.FAILED_UPDATE_FROM_DB);
        }
        logger.info("update detail check success.");
        //查询订单是否已经验收完成，如果是，订单状态修改到验收完成状态
        List<SellOrderBackDetail> details = sellOrderBackDetailMapper.getByBackOrderId(order.getId());
        boolean result = true;
        for (SellOrderBackDetail item : details) {
            if (item.getCheckStatus() == null || !item.getCheckStatus()) {
                result = false;
                break;
            }
        }
        if (result) {
            logger.info("detail is check all success");
            order.setStatus(SellBackStatus.QUALITY_CHECKED.name());
            order.setQualityCheckUser(user.getNickname());
            order.setQualityCheckTime(new Date());
            order.setUpdatedTime(new Date());
            order.setUpdatedBy(user.getNickname());
            sellOrderBackMapper.updateByPrimaryKeySelective(order);
        }
    }

    @Transactional
    public void qualityCheckOrder(SellOrderBack order, SellBackCheck check, User user) {
        //直接修改所有的详情验收结果和订单状态
        check.setUpdatedTime(new Date());
        check.setUpdatedBy(user.getNickname());
        check.setCheckUser(user.getNickname());
        check.setCheckStatus(true);
        int detailCount = sellOrderBackDetailMapper.updateCheckStatusByBackOrderId(check);
        if (detailCount <= 0) {
            throw new BizRuntimeException(ErrorCode.FAILED_UPDATE_FROM_DB);
        }
        logger.info("update detail check success by orderId:{}", check.getBackOrderId());
        order.setStatus(SellBackStatus.QUALITY_CHECKED.name());
        order.setQualityCheckUser(user.getNickname());
        order.setQualityCheckTime(new Date());
        order.setUpdatedTime(new Date());
        order.setUpdatedBy(user.getNickname());
        sellOrderBackMapper.updateByPrimaryKeySelective(order);
    }


    /**
     * 质量复核取消
     * @param check
     * @param user
     * @throws BizException
     */
    @Transactional
    public void cancelQualityCheck(SellBackCheck check, User user) throws BizException {
        SellOrderBack order = sellOrderBackMapper.selectByPrimaryKey(check.getBackOrderId());
        if (order == null || !user.getCompanyId().equals(order.getCompanyId())) {
            logger.warn("get sell order back fail or user company and order company is not match. orderId:{}", check.getBackOrderId());
            throw new BizException(ErrorCode.SELL_BACK_ORDER_GET_FAIL);
        }
        if (!SellBackStatus.QUALITY_CHECKED.name().equalsIgnoreCase(order.getStatus()) &&
                !SellBackStatus.BACK_RECEIVE.name().equalsIgnoreCase(order.getStatus())) {
            logger.warn("order:{} is not in QUALITY_CHECKED status, check cannot do. user:{}", order.getId(), user.getId());
            throw new BizException(ErrorCode.SELL_BACK_QUALITY_CHECK_CANCEL_ERROR);
        }

        //如果存在详情ID，则认为是取消一笔详情
        Long detailId = check.getDetailId();
        SellOrderBackDetail detail = null;
        if (detailId != null) {
            detail = sellOrderBackDetailMapper.selectByPrimaryKey(detailId);
            if (detail == null) {
                logger.warn("get detail fail by id:{}", detail);
                throw new BizException(ErrorCode.SELL_BACK_DETAIL_GET_FAIL);
            }
        }

        // 修改状态和登记质量验收意见
        order.setStatus(SellBackStatus.BACK_RECEIVE.name());
        order.setUpdatedTime(new Date());
        order.setUpdatedBy(user.getNickname());
        int count = sellOrderBackMapper.updateByPrimaryKeySelective(order);
        if (count <= 0) {
            logger.warn("update status fail.");
            throw new BizRuntimeException(ErrorCode.FAILED_UPDATE_FROM_DB);
        }
        //登记详情的审核状态和意见取消
        check.setBackOrderId(order.getId());
        check.setDetailId(detail == null ? null : detail.getId());
        check.setBadPlan(null);
        check.setBadReason(null);
        check.setCheckResult(null);
        check.setUpdatedBy(user.getNickname());
        check.setUpdatedTime(new Date());
        check.setCheckStatus(false);
        check.setCheckTempMethod(null);
        check.setCheckTime(null);
        check.setCheckUser(null);
        int detailCount = sellOrderBackDetailMapper.updateCheckStatus(check);
        if (detailCount <= 0) {
            logger.warn("update sell back order detail check status fail. backOrderId:{}", order.getId());
            throw new BizRuntimeException(ErrorCode.FAILED_UPDATE_FROM_DB);
        }
    }

    /**
     * 终审
     * @param check
     * @param user
     * @throws BizException
     */
    @Transactional
    public void finalCheck(SellBackCheck check, User user) throws BizException {
        SellOrderBack order = sellOrderBackMapper.selectByPrimaryKey(check.getBackOrderId());
        if (order == null || !user.getCompanyId().equals(order.getCompanyId())) {
            logger.warn("get sell order back fail or user company and order company is not match. orderId:{}", check.getBackOrderId());
            throw new BizException(ErrorCode.SELL_BACK_ORDER_GET_FAIL);
        }
        if (!SellBackStatus.QUALITY_CHECKED.name().equalsIgnoreCase(order.getStatus())) {
            logger.warn("order:{} is not in QUALITY_CHECKED status, check cannot do. user:{}", order.getId(), user.getId());
            throw new BizException(ErrorCode.SELL_BACK_QUALITY_CHECK_CANCEL_ERROR);
        }
        List<SellOrderBackDetail> details = sellOrderBackDetailMapper.getByBackOrderId(order.getId());
        if (details.isEmpty()) {
            logger.warn("order:{} get detail list is empty.", order.getId());
            throw new BizException(ErrorCode.SELL_BACK_DETAIL_GET_FAIL);
        }

        //添加入库流水信息
        List<RepertoryInfo> infos = createRepertoryInfos(user, order, details);
        int inCount = repertoryInfoMapper.insertBatch(infos);
        if (inCount <= 0) {
            logger.warn("repertoryInfo insert batch fail.");
            throw new BizRuntimeException(ErrorCode.FAILED_INSERT_FROM_DB);
        }
        // 修改状态为终审通过
        order.setStatus(SellBackStatus.FINAL_CHECKED.name());
        order.setFinalCheckTime(new Date());
        order.setFinalCheckUser(user.getNickname());
        order.setUpdatedBy(user.getNickname());
        order.setUpdatedTime(new Date());
        int count = sellOrderBackMapper.updateByPrimaryKeySelective(order);
        if (count <= 0) {
            logger.warn("update back order status to final_check fail. orderId:{}", order.getId());
            throw new BizRuntimeException(ErrorCode.FAILED_UPDATE_FROM_DB);
        }

        // 生产一个销售退货的事件
        rabbitmqQueueConfig.sendMessage("SellOrderBackService", RabbitmqQueueConfig.ORDER_SELL_BACK, order);
    }

    private List<RepertoryInfo> createRepertoryInfos(User user, SellOrderBack order, List<SellOrderBackDetail> detailList) {
        List<RepertoryInfo> infos = new ArrayList<>();
        for (SellOrderBackDetail detail : detailList) {
            RepertoryInfo item = new RepertoryInfo();
            item.setCompanyId(order.getCompanyId());
            item.setWarehouseId(order.getWarehouseId());
            item.setLocation(detail.getLocation());
            item.setInUserId(user.getId());
            item.setGoodsId(detail.getGoodsId());
            item.setBatchCode(detail.getBatchCode());
            item.setFactoryId(detail.getGoods() == null ? null : detail.getGoods().getFactoryId());
            item.setInQuantity(detail.getRightQuantity()); //取的是合格的数量
            item.setQuantity(detail.getRightQuantity());
            item.setOnWayQuantity(BigDecimal.ZERO); //初始在单数全部设置为0
//            item.setBuyPrice(detail.getPrice());
            item.setExp(false);
            item.setSaleEnable(true);
            item.setProductDate(detail.getProductDate());
            item.setExpDate(detail.getExpDate());
            item.setInDate(new Date());
//            item.setSupplierId(order.getSupplierId());
//            item.setSupplierContactId(order.getSupplierContactId());
//            item.setBuyerId(order.getBuyerId());
            item.setOrderId(order.getId());
            item.setOrderDetailId(detail.getId());
//            item.setSaleSate(detail.getSaleState());
            item.setCreateBy(user.getNickname());
            item.setCreateTime(new Date());
            item.setUpdateBy(user.getNickname());
            item.setUpdateTime(new Date());
            item.setCustomerId(order.getCustomerId());
            item.setCustomerRepId(order.getCustomerRepId());
            item.setSaleId(order.getSaleId());
            item.setBackPrice(detail.getBackPrice());
            item.setRefType(RepertoryRefType.SELL_BACK.name());
            item.setRefOrderId(order.getId());

            infos.add(item);
        }
        return infos;
    }

    /**
     * 删除销售退款单
     * @param backOrderId
     * @param user
     * @throws BizException
     */
    @Transactional
    public void removeBackOrder(Long backOrderId, User user) throws BizException {
        SellOrderBack order = sellOrderBackMapper.selectByPrimaryKey(backOrderId);
        if (order == null || !user.getCompanyId().equals(order.getCompanyId())) {
            logger.warn("get sell order back fail or user company and order company is not match. orderId:{}", backOrderId);
            throw new BizException(ErrorCode.SELL_BACK_ORDER_GET_FAIL);
        }
        if (SellBackStatus.FINAL_CHECKED.name().equalsIgnoreCase(order.getStatus())) {
            logger.warn("order:{} is in FINAL_CHECKED status, remove cannot do. user:{}", order.getId(), user.getId());
            throw new BizException(ErrorCode.SELL_BACK_REMOVE_STATUS_ERROR);
        }
        //直接修改到删除状态
        order.setStatus(SellBackStatus.DELETE.name());
        order.setUpdatedBy(user.getNickname());
        order.setUpdatedTime(new Date());
        int count = sellOrderBackMapper.updateByPrimaryKeySelective(order);
        if (count <= 0) {
            logger.warn("remove back order update status fail. order:{}", order.getId());
            throw new BizRuntimeException(ErrorCode.FAILED_UPDATE_FROM_DB);
        }
        if(order.getHaveSellOrder()) {
            //把销售单明细中的退货数减去
            int count1 = sellOrderBackDetailMapper.releaseAlreadyBackQuantity(order.getId());
            if (count1 <= 0) {
                logger.warn("update sell order detail back quantity fail by backOrderId:{}", order.getId());
                throw new BizRuntimeException(ErrorCode.FAILED_UPDATE_FROM_DB);
            }
        }
    }

    /**
     * 销售退货收货
     * @param back
     * @param user
     */
    public void receive(SellOrderBack back, User user) throws BizException {
        SellOrderBack order = sellOrderBackMapper.selectByPrimaryKey(back.getId());
        if (order == null || !order.getCompanyId().equals(user.getCompanyId())) {
            logger.warn("get back order fail by id:{}", back.getId());
            throw new BizException(ErrorCode.SELL_BACK_ORDER_GET_FAIL);
        }
        if (!SellBackStatus.INIT_QUALITY_CHECKED.name().equalsIgnoreCase(order.getStatus()) &&
                !SellBackStatus.BACK_RECEIVE.name().equalsIgnoreCase(order.getStatus())) {
            logger.warn("order status can not do receive action. order status:{}", order.getStatus());
            throw new BizException(ErrorCode.SELL_BACK_RECEIVE_STATUS_ERROR);
        }
        //如果存在了，直接调用修改逻辑
        back.setStatus(SellBackStatus.BACK_RECEIVE.name());
        back.setUpdatedBy(user.getNickname());
        back.setUpdatedTime(new Date());
        sellOrderBackMapper.updateByPrimaryKeySelective(back);
    }
}
