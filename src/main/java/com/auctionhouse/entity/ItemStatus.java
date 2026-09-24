package com.auctionhouse.entity;

public enum ItemStatus {
        ACTIVE("进行中"),
        ENDED("已结束"),
        CANCELLED("已取消");
        private String statusName;
        ItemStatus(String statusName){//枚举类是默认private，毕竟就那几种情况，强制创建后所有人不允许修改（private可写，不写也默认private）
            this.statusName=statusName;
        }


    public String getStatusName() {
        return statusName;
    }
    //枚举是常量，不能改
}
