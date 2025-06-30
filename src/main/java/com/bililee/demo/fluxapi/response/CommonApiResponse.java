package com.bililee.demo.fluxapi.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 API 响应包装类。
 *
 * @param <T> data 字段的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonApiResponse<T> {
    private int status_code;  // 状态码，0表示成功，非0表示错误
    private String status_msg; // 状态信息，成功或错误描述
    private T data;           // 实际返回的数据

    // 静态工厂方法用于创建成功响应
    public static <T> CommonApiResponse<T> success(T data) {
        return new CommonApiResponse<>(0, "Success", data);
    }

    // 静态工厂方法用于创建不带数据的成功响应
    public static CommonApiResponse<Void> success() {
        return new CommonApiResponse<>(0, "Success", null);
    }

    // 静态工厂方法用于创建错误响应
    public static <T> CommonApiResponse<T> error(int statusCode, String message) {
        return new CommonApiResponse<>(statusCode, message, null);
    }
}