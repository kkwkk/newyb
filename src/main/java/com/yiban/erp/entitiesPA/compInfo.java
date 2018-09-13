package com.yiban.erp.entitiesPA;

import java.util.Date;

public class compInfo {

    private int id;
    private String corpName;
    private Long saleTotal;
    private String top3Name1;
    private Long top3Debtnum1;
    private String top3Name2;
    private Long top3Debtnum2;
    private String top3Name3;
    private Long top3Debtnum3;
    private Date createdTime;


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCorpName() {
        return corpName;
    }

    public void setCorpName(String corpName) {
        this.corpName = corpName;
    }

    public Long getSaleTotal() {
        return saleTotal;
    }

    public void setSaleTotal(Long saleTotal) {
        this.saleTotal = saleTotal;
    }

    public String getTop3Name1() {
        return top3Name1;
    }

    public void setTop3Name1(String top3Name1) {
        this.top3Name1 = top3Name1;
    }

    public Long getTop3Debtnum1() {
        return top3Debtnum1;
    }

    public void setTop3Debtnum1(Long top3Debtnum1) {
        this.top3Debtnum1 = top3Debtnum1;
    }

    public String getTop3Name2() {
        return top3Name2;
    }

    public void setTop3Name2(String top3Name2) {
        this.top3Name2 = top3Name2;
    }

    public Long getTop3Debtnum2() {
        return top3Debtnum2;
    }

    public void setTop3Debtnum2(Long top3Debtnum2) {
        this.top3Debtnum2 = top3Debtnum2;
    }

    public String getTop3Name3() {
        return top3Name3;
    }

    public void setTop3Name3(String top3Name3) {
        this.top3Name3 = top3Name3;
    }

    public Long getTop3Debtnum3() {
        return top3Debtnum3;
    }

    public void setTop3Debtnum3(Long top3Debtnum3) {
        this.top3Debtnum3 = top3Debtnum3;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }
}
