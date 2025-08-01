package com.bililee.demo.fluxapi.model;

import lombok.Data;

@Data
public class ApiData {
    private String id;
    private String content;
    private long expireAt; // 新增字段，表示数据的有效时间戳
}
