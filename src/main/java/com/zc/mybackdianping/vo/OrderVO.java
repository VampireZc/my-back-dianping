package com.zc.mybackdianping.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderVO {
    private Long id;
    private Integer status;
    private Integer payType;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private Long payValue;
    private Long actualValue;
    private String rules;
    private Long shopId;
    private String shopName;
    private String shopImg;
    private Integer stock;
    private LocalDateTime endTime;

}
