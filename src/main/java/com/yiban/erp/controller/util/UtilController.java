package com.yiban.erp.controller.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.stuxuhai.jpinyin.PinyinException;
import com.github.stuxuhai.jpinyin.PinyinHelper;
import com.yiban.erp.dao.BillTradeLogMapper;
import com.yiban.erp.daoPA.paDataMapper;
import com.yiban.erp.entities.BillTradeLog;
import com.yiban.erp.entitiesPA.*;
import com.yiban.erp.exception.*;
import com.yiban.erp.util.AESUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/util")
public class UtilController {
    private static final Logger logger = LoggerFactory.getLogger(UtilController.class);

    @Autowired
    private BillTradeLogMapper billTradeLogMapper;

    @Autowired
    private paDataMapper paDataMapper;
    //通过注解引入配置
    @Resource(name = "defaultThreadPool")
    private ThreadPoolTaskExecutor executor;
    /**
     * 获取拼音缩写
     *
     * @return
     */
    @RequestMapping(value = "/pinyinAbbr", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPinyinAbbr(@RequestBody Map map) throws Exception {
        String name = (String) map.get("name");
            if (StringUtils.isEmpty(name)) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok().body(PinyinHelper.getShortPinyin(name).toUpperCase());
        } catch (PinyinException e) {
            logger.error("Failed to translate to pinyin, string={}", name);
            throw new BizRuntimeException(ErrorCode.FAILED_PINGYIN_EXCEPTION, e.toString());
        }
    }

    /**
     * 获取推送的Tradelog
     *
     * @return
     */
//    @RequestMapping(value = "/tradelog", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<String> receiveTradeLog(@RequestBody String body) throws Exception {
//        String result = AESUtil.decrypt(body);
//        if (StringUtils.isNotEmpty(result)) {
//            try {
//                BillTradeLog billTradeLog = new BillTradeLog();
//                billTradeLog.setBody(result);
//                billTradeLog.setCreatedTime(new Date());
//                billTradeLogMapper.insert(billTradeLog);
//            } catch (Exception ex) {
//                logger.error(ex.getMessage());
//            }
//
//            JSONObject returnObj = new JSONObject();
//            returnObj.put("responseMessage", "详情推送成功!");
//            returnObj.put("responseCode", "000000");
//
//            JSONObject resultObj = new JSONObject();
//            resultObj.put("response", returnObj);
//            return ResponseEntity.ok().body(resultObj.toString());
//        }
//        return ResponseEntity.badRequest().build();
//    }

    @RequestMapping(value = "/tradelog", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
            public ResponseEntity<String> receiveTradeLog(@RequestBody String body) throws Exception {

                String result = AESUtil.decrypt(body);
                try{
                    if (StringUtils.isNotEmpty(result)){
                        newTradeLog(result);
                    }

                }catch(Exception e){
                    logger.error(e.getMessage());
                }finally{
                    if (StringUtils.isNotEmpty(result)) {
                        try {
                            BillTradeLog billTradeLog = new BillTradeLog();
                            billTradeLog.setBody(result);
                            billTradeLog.setCreatedTime(new Date());
                            billTradeLogMapper.insert(billTradeLog);
                        } catch (Exception ex) {
                            logger.error(ex.getMessage());
                        }

                        JSONObject returnObj = new JSONObject();
                        returnObj.put("responseMessage", "详情推送成功!");
                        returnObj.put("responseCode", "000000");

                        JSONObject resultObj = new JSONObject();
                        resultObj.put("response", returnObj);
                        return ResponseEntity.ok().body(resultObj.toString());
                }
        }
        return ResponseEntity.badRequest().build();
    }

    /**
     * 将trade_log表数据插入pa_head和pa_detail
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */

    @Transactional
    @RequestMapping(value="/dealData",method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> dealData() throws ExecutionException, InterruptedException {
        int size = billTradeLogMapper.countNum();
        logger.warn("size-----------------------"+size);
        int zc = size/10000;
        Long start = System.currentTimeMillis();
        logger.warn("begin---------------"+start);
        //使用Future方式执行多任务
        //生成一个集合
        List<Future> futures = new ArrayList<>();
        List<String> billTradeLogs;
        for(int i=0;i<=zc;i++){
                billTradeLogs = billTradeLogMapper.selectAll(i*10000,10000);
            for(int m=0;m<billTradeLogs.size();m++){
                int finalM = m;
                List<String> finalBillTradeLogs = billTradeLogs;
                Future<?> future = executor.submit(() -> {
                    newTradeLog(finalBillTradeLogs.get(finalM));
               });
                futures.add(future);
           }
        }
        logger.warn("end with-------------------"+(System.currentTimeMillis()-start));
        return ResponseEntity.ok().build();
    }

    /**
     * 提取pa_head表中的所有公司前三客户和近一年销售额并插入company_info
     * @return
     */
    @Transactional
    @RequestMapping(value="/dealData2",method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> dealData2(){
       List<compInfo> comps = paDataMapper.allComp();

       for(int i=0;i<comps.size();i++){
           List<topConsumer> tops = paDataMapper.topConsumer(comps.get(i).getCorpName());
           if(tops.size()>=1){
               comps.get(i).setTop3Name1(tops.get(0).getGfmc());
               comps.get(i).setTop3Debtnum1(tops.get(0).getHj());
           }
           if(tops.size()>=2){
               comps.get(i).setTop3Name2(tops.get(1).getGfmc());
               comps.get(i).setTop3Debtnum2(tops.get(1).getHj());
           }

           if(tops.size()>=3){
               comps.get(i).setTop3Name3(tops.get(2).getGfmc());
               comps.get(i).setTop3Debtnum3(tops.get(2).getHj());
           }
       }
        logger.warn("--------"+comps.size());
       for(int i=0;i<comps.size();i++){
           paDataMapper.insertComps(comps.get(i));
       }
       return ResponseEntity.ok().build();
    }

    private  void newTradeLog(String body){
        String  temp = body.replaceAll("\\\\","");
        String data=temp.substring(1,temp.length()-1);
        JSONObject text = (JSONObject)JSON.parse(data);
        JSONObject content=(JSONObject)text.get("pushData");
        JSONArray details = content.getJSONArray("invoiceDetails");
        JSONArray heads = content.getJSONArray("invoiceHeads");

        if(heads != null){
            for(int i=0;i<heads.size();i++){
                TradeHead tradeHead = JSON.parseObject(heads.getString(i),TradeHead.class);
                tradeHead.setCreatedTime(new Date());
                //logger.warn("insertHead--------------"+tradeHead.toString());
                int j = paDataMapper.insertHead(tradeHead);
                String cropName =tradeHead.getXfmc();
                /**提取前三客户及年销售额*/
                compInfo compInfo = new compInfo();

                compInfo.setCorpName(cropName);
                compInfo.setSaleTotal(paDataMapper.getSellTotal(cropName));

                List<topConsumer> tops = paDataMapper.topConsumer(cropName);
                if(tops.size()>=1){
                    compInfo.setTop3Name1(tops.get(0).getGfmc());
                    compInfo.setTop3Debtnum1(tops.get(0).getHj());
                }
                if(tops.size()>=2){
                    compInfo.setTop3Name2(tops.get(1).getGfmc());
                    compInfo.setTop3Debtnum2(tops.get(1).getHj());
                }
                if(tops.size()>=3){
                    compInfo.setTop3Name3(tops.get(2).getGfmc());
                    compInfo.setTop3Debtnum3(tops.get(2).getHj());
                }
                if(paDataMapper.selectComp(cropName)<0){
                    paDataMapper.insertComps(compInfo);
                }else{
                    paDataMapper.updComp(compInfo);
                }
                compInfo=null;
                tradeHead=null;
            }
        }
        if(details!=null){
            for(int i=0;i<details.size();i++){
                TradeDetail tradeDetail = JSON.parseObject(details.getString(i),TradeDetail.class);
                tradeDetail.setCreatedTime(new Date());
                //logger.warn("insertDetail--------------");
                paDataMapper.insertDetail(tradeDetail);
                tradeDetail=null;
            }
        }
        temp=null;
        data=null;
        text=null;
        content=null;
        details=null;
        heads=null;
    }
    @RequestMapping(value = "/exception", method = RequestMethod.GET)
    public ResponseEntity<String> testException(@RequestParam("code") Integer code) throws Exception {
        logger.info("test exception handler code:", code);
        ErrorInfo testExtra = new ErrorInfo();
        testExtra.setCode(code);
        testExtra.setMessage("test exception handler");
        switch (code) {
            case 1:
                throw new BizException(ErrorCode.FAILED_DELETE_FROM_DB);
            case 2:
                throw new BizException(ErrorCode.CUSTOMER_DEL_PARAMS_EMPTY, testExtra);
            case 3:
                throw new BizRuntimeException(ErrorCode.FAILED_PINGYIN_EXCEPTION);
            case 4:
                throw new BizRuntimeException(ErrorCode.FAILED_PINGYIN_EXCEPTION, "PinyinException");
            case 5:
                throw new IllegalArgumentException("IllegalArgumentException");
            case 6:
                throw new RuntimeException("RuntimeException");
            case 7:
                throw new Exception("Exception");
            case 8:
                throw new PermissionException(ErrorCode.ACCESS_PERMISSION);
            default:
                return ResponseEntity.ok().body("code > 8 ok");
        }
    }

}
