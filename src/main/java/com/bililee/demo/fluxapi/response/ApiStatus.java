package com.bililee.demo.fluxapi.response;

/**
 * API 响应状态码和状态消息常量定义
 * 
 * <p>本类统一管理所有API响应的状态码和对应的状态消息，确保系统中状态码的一致性。</p>
 * 
 * <h3>状态码规范：</h3>
 * <ul>
 *   <li>0: 表示成功</li>
 *   <li>4xx: 客户端错误（请求有误）</li>
 *   <li>5xx: 服务器错误（服务端异常）</li>
 *   <li>1xxx: 业务错误（自定义业务异常）</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建成功响应
 * if (ApiStatus.isSuccess(response.getStatusCode())) {
 *     // 处理成功逻辑
 * }
 * 
 * // 创建错误响应
 * return SpecificDataResponse.error(ApiStatus.INTERNAL_SERVER_ERROR_CODE, 
 *                                   ApiStatus.INTERNAL_SERVER_ERROR_MSG);
 * }</pre>
 * 
 * @author bililee
 * @since 1.0.0
 */
public final class ApiStatus {

    // ============ 成功状态 ============
    /** 操作成功状态码 */
    public static final int SUCCESS_CODE = 0;
    /** 操作成功状态消息 */
    public static final String SUCCESS_MSG = "success";

    // ============ 客户端错误 (4xx) ============
    /** 请求参数错误状态码 */
    public static final int BAD_REQUEST_CODE = 400;
    /** 请求参数错误状态消息 */
    public static final String BAD_REQUEST_MSG = "请求参数错误";

    /** 未授权访问状态码 */
    public static final int UNAUTHORIZED_CODE = 401;
    /** 未授权访问状态消息 */
    public static final String UNAUTHORIZED_MSG = "未授权访问";

    /** 访问被禁止状态码 */
    public static final int FORBIDDEN_CODE = 403;
    /** 访问被禁止状态消息 */
    public static final String FORBIDDEN_MSG = "访问被禁止";

    /** 资源未找到状态码 */
    public static final int NOT_FOUND_CODE = 404;
    /** 资源未找到状态消息 */
    public static final String NOT_FOUND_MSG = "资源未找到";

    // ============ 服务器错误 (5xx) ============
    /** 服务内部错误状态码 */
    public static final int INTERNAL_SERVER_ERROR_CODE = 500;
    /** 服务内部错误状态消息 */
    public static final String INTERNAL_SERVER_ERROR_MSG = "服务内部错误，请稍后重试";

    /** 网关错误状态码 */
    public static final int BAD_GATEWAY_CODE = 502;
    /** 网关错误状态消息 */
    public static final String BAD_GATEWAY_MSG = "网关错误";

    /** 服务不可用状态码 */
    public static final int SERVICE_UNAVAILABLE_CODE = 503;
    /** 服务不可用状态消息 */
    public static final String SERVICE_UNAVAILABLE_MSG = "服务暂时不可用，请稍后重试";

    /** 网关超时状态码 */
    public static final int GATEWAY_TIMEOUT_CODE = 504;
    /** 网关超时状态消息 */
    public static final String GATEWAY_TIMEOUT_MSG = "网关超时";

    // ============ 业务错误 (1xxx) ============
    /** 业务处理异常状态码 */
    public static final int BUSINESS_ERROR_CODE = 1001;
    /** 业务处理异常状态消息 */
    public static final String BUSINESS_ERROR_MSG = "业务处理异常";

    /** 远程服务调用失败状态码 */
    public static final int REMOTE_SERVICE_ERROR_CODE = 1002;
    /** 远程服务调用失败状态消息 */
    public static final String REMOTE_SERVICE_ERROR_MSG = "远程服务调用失败";

    /** 缓存操作异常状态码 */
    public static final int CACHE_ERROR_CODE = 1003;
    /** 缓存操作异常状态消息 */
    public static final String CACHE_ERROR_MSG = "缓存操作异常";

    /** 请求超时状态码 */
    public static final int TIMEOUT_ERROR_CODE = 1004;
    /** 请求超时状态消息 */
    public static final String TIMEOUT_ERROR_MSG = "请求超时";

    /** 网络连接异常状态码 */
    public static final int NETWORK_ERROR_CODE = 1005;
    /** 网络连接异常状态消息 */
    public static final String NETWORK_ERROR_MSG = "网络连接异常";

    private ApiStatus() {
        // 防止实例化
    }

    /**
     * 判断状态码是否为成功状态
     * 
     * @param statusCode 状态码
     * @return 如果状态码为0则返回true，否则返回false
     */
    public static boolean isSuccess(int statusCode) {
        return statusCode == SUCCESS_CODE;
    }

    /**
     * 判断状态码是否为客户端错误
     * 
     * @param statusCode 状态码
     * @return 如果状态码在400-499范围内则返回true，否则返回false
     */
    public static boolean isClientError(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * 判断状态码是否为服务器错误
     * 
     * @param statusCode 状态码
     * @return 如果状态码在500-599范围内则返回true，否则返回false
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * 判断状态码是否为业务错误
     * 
     * @param statusCode 状态码
     * @return 如果状态码在1000-1999范围内则返回true，否则返回false
     */
    public static boolean isBusinessError(int statusCode) {
        return statusCode >= 1000 && statusCode < 2000;
    }
}
