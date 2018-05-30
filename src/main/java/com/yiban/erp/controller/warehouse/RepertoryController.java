package com.yiban.erp.controller.warehouse;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.yiban.erp.dao.RepertoryInfoMapper;
import com.yiban.erp.dto.RepertoryQuery;
import com.yiban.erp.dto.RepertorySelectQuery;
import com.yiban.erp.entities.GoodsAttributeRef;
import com.yiban.erp.entities.RepertoryInfo;
import com.yiban.erp.entities.User;
import com.yiban.erp.service.warehouse.RepertoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/repertory")
public class RepertoryController {

    private static final Logger logger = LoggerFactory.getLogger(RepertoryController.class);

    @Autowired
    private RepertoryService repertoryService;
    @Autowired
    private RepertoryInfoMapper repertoryInfoMapper;


    @RequestMapping(value = "/list", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> list(@AuthenticationPrincipal User user,
                                       @RequestBody RepertoryQuery repertoryQuery) {
        JSONObject result = repertoryService.getCurrentRepertory(user, repertoryQuery);
        return ResponseEntity.ok().body(JSON.toJSONString(result, SerializerFeature.DisableCircularReferenceDetect));
    }


    @RequestMapping(value = "/select", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> selectList(@RequestBody RepertorySelectQuery query,
                                             @AuthenticationPrincipal User user) throws Exception {
        logger.info("select as RepertorySelectQuery {}" ,query);


        List<GoodsAttributeRef> attrs = query.getCurrAttributes();
        List<Long> goods = new ArrayList<>();
        for(int i=0; i<attrs.size();i++){
            if(!("").equals(attrs.get(i).getAttValue())&&attrs.get(i).getAttValue()!=null){
                Long m = repertoryInfoMapper.getGoodsId(attrs.get(i).getAttId(),attrs.get(i).getAttValue());
                goods.add(m);
            }
        }
        query.setCompanyId(user.getCompanyId());
        query.setByPage(true);
        Integer count = repertoryInfoMapper.querySelectCount(query,goods);
        List<RepertoryInfo> infos = new ArrayList<>();
        if (count == null || count <= 0) {
            count = 0;
        } else {
            infos = repertoryService.querySelectList(query);
        }
        JSONObject response = new JSONObject();
        response.put("count", count);
        response.put("data", infos);
        return ResponseEntity.ok().body(JSON.toJSONString(response, SerializerFeature.DisableCircularReferenceDetect));
    }

}
