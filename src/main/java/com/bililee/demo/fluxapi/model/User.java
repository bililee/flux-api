package com.bililee.demo.fluxapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // 自动生成 getter, setter, toString, equals, hashCode
@NoArgsConstructor // 自动生成无参构造函数
@AllArgsConstructor // 自动生成全参构造函数
public class User {
    private String id;
    private String name;
    private String email;
}