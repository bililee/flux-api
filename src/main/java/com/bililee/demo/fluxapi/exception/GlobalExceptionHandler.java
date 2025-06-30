package com.bililee.demo.fluxapi.exception;


import com.bililee.demo.fluxapi.response.CommonApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice // 标记这是一个全局的控制器增强，用于处理异常
public class GlobalExceptionHandler {

    /**
     * 处理 ResponseStatusException 异常，将其转换为统一的 CommonApiResponse 错误格式。
     *
     * @param ex ResponseStatusException 实例，通常包含 HTTP 状态码和原因。
     * @return 包含错误信息的 ResponseEntity。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleResponseStatusException(ResponseStatusException ex) {
        // 从 ResponseStatusException 中获取 HTTP 状态码和原因
        HttpStatus status = (HttpStatus) ex.getStatusCode();
        String reason = ex.getReason();

        // 构建 CommonApiResponse 错误响应。
        // 这里使用 HTTP 状态码作为 CommonApiResponse 的 status_code，或者可以自定义错误码映射。
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(status.value(), reason);

        // 返回 ResponseEntity，确保 HTTP 状态码也与 ResponseStatusException 匹配。
        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * 捕获其他未被特定处理的通用异常，作为内部服务器错误。
     *
     * @param ex 任何其他未捕获的异常。
     * @return 包含错误信息的 ResponseEntity。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<Void>> handleGenericException(Exception ex) {
        System.err.println("捕获到未处理的异常: " + ex.getMessage());
        ex.printStackTrace(); // 打印堆栈跟踪，便于调试

        // 构建 CommonApiResponse 错误响应，统一为 500 内部服务器错误。
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred: " + ex.getMessage() // 生产环境通常不返回详细的错误信息
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}