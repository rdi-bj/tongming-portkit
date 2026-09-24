package com.jinw.web.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;

/**
 * 类 {@code RestResult } 响应参数
 * <p> 封装后台返回给前端的响应参数</p>
 *
 * @author lilw
 * @since 2021/8/11  17:18
 */
@Schema(description = "通用响应结果")
public class RestResult<T> {

    /**
     * 状态码
     */
    public static final String CODE_TAG = "code";

    /**
     * 返回内容
     */
    public static final String MSG_TAG = "msg";

    /**
     * 数据对象
     */
    public static final String DATA_TAG = "data";

    @JsonProperty(index = 10)
    @Schema(description = "状态码")
    private int code;

    @JsonProperty(index = 20)
    @Schema(description = "响应消息")
    private String msg;

    @JsonProperty(index = 30)
    @Schema(description = "响应数据")
    private T data;

    @JsonProperty(index = 50)
    @Schema(description = "附加数据")
    private Map<String, Object> params;

    /**
     * 初始化一个新创建的 RestResult 对象，使其表示一个空消息。
     */
    public RestResult() {
    }

    /**
     * 初始化一个新创建的 RestResult 对象
     *
     * @param code 状态码
     * @param msg  返回内容
     */
    public RestResult(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 初始化一个新创建的 RestResult 对象
     *
     * @param code 状态码
     * @param msg  返回内容
     * @param data 数据对象
     */
    public RestResult(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 返回成功消息
     *
     * @return 成功消息
     */
    public static RestResult success() {
        return RestResult.success("操作成功");
    }

    /**
     * 返回成功数据
     *
     * @return 成功消息
     */
    public static <T> RestResult<T> success(T data) {
        return RestResult.success("操作成功", data);
    }

    /**
     * 返回成功消息
     *
     * @param msg 返回内容
     * @return 成功消息
     */
    public static RestResult successWithMsg(String msg) {
        return RestResult.success(msg, null);
    }

    /**
     * 返回成功消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 成功消息
     */
    public static RestResult success(String msg, Object data) {
        return new RestResult(HttpStatus.SUCCESS, msg, data);
    }

    /**
     * 返回成功消息
     *
     * @param lenth 返回内容
     * @return 成功消息
     */
    public static RestResult success(int lenth) {
        return RestResult.success("操作成功");
    }

    /**
     * 返回错误消息
     *
     * @return
     */
    public static RestResult error() {
        return RestResult.error("操作失败");
    }

    /**
     * 返回错误消息
     *
     * @param msg 返回内容
     * @return 警告消息
     */
    public static RestResult error(String msg) {
        return RestResult.error(msg, null);
    }

    /**
     * 返回错误消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 警告消息
     */
    public static RestResult error(String msg, Object data) {
        return new RestResult(HttpStatus.ERROR, msg, data);
    }

    /**
     * 返回错误消息
     *
     * @param code 状态码
     * @param msg  返回内容
     * @return 警告消息
     */
    public static RestResult error(int code, String msg) {
        return new RestResult(code, msg, null);
    }

    /**
     * 禁止使用，响应数据请统一调用setData进行存放。
     * 该方法为了兼容老版本的RestResult而临时定制
     * 后续版本会删除该方法
     *
     * @param key
     * @param value
     */
    @Deprecated
    public void put(String key, Object value) {
        if (DATA_TAG.equals(key)) {
            this.data = (T) value;
        } else if (CODE_TAG.equals(key)) {
            this.code = (int) value;
        } else if (MSG_TAG.equals(key)) {
            this.msg = (String) value;
        }
        if (params == null) {
            params = new HashMap<>();
        }
        params.put(key, value);
    }

    public int getCode() {
        return code;
    }

    public RestResult<T> setCode(int code) {
        this.code = code;
        return this;
    }

    public String getMsg() {
        return msg;
    }

    public RestResult<T> setMsg(String msg) {
        this.msg = msg;
        return this;
    }

    public T getData() {
        return data;
    }

    public RestResult<T> setData(T data) {
        this.data = data;
        return this;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
