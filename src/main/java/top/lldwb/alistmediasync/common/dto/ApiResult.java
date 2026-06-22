package top.lldwb.alistmediasync.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 统一 API 响应包装类
 * <p>
 * 所有 REST API 端点均返回此格式的 JSON 响应，
 * 确保前端能统一处理成功和错误两种情况。
 * </p>
 *
 * @param <T> 响应数据的类型
 * @author AList-Media-Sync
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {

    /** HTTP 状态码 */
    private final int code;

    /** 提示消息（成功或错误描述） */
    private final String message;

    /** 响应数据（成功时返回，可能为 null） */
    private final T data;

    private ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 创建成功响应（HTTP 200）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return ApiResult 实例
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, "操作成功", data);
    }

    /**
     * 创建成功响应（HTTP 200），无数据
     *
     * @return ApiResult 实例
     */
    public static <T> ApiResult<T> success() {
        return new ApiResult<>(200, "操作成功", null);
    }

    /**
     * 创建成功响应，自定义消息
     *
     * @param message 成功消息
     * @param data    响应数据
     * @param <T>     数据类型
     * @return ApiResult 实例
     */
    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>(200, message, data);
    }

    /**
     * 创建自定义状态码响应
     *
     * @param code    HTTP/业务状态码
     * @param message 提示消息
     * @param data    响应数据
     * @param <T>     数据类型
     * @return ApiResult 实例
     */
    public static <T> ApiResult<T> of(int code, String message, T data) {
        return new ApiResult<>(code, message, data);
    }

    /**
     * 创建错误响应
     *
     * @param code    业务错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResult 实例
     */
    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, message, null);
    }

    /**
     * 创建错误响应，携带错误数据
     *
     * @param code    业务错误码
     * @param message 错误消息
     * @param data    错误详情数据
     * @param <T>     数据类型
     * @return ApiResult 实例
     */
    public static <T> ApiResult<T> error(int code, String message, T data) {
        return new ApiResult<>(code, message, data);
    }
}
