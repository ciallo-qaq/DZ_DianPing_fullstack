package com.dzdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class Result {
    private Integer code;
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    public Result() {
        this(StatusCode.SUCCESS);
    }

    public Result(StatusCode statusCode) {
        this(statusCode, statusCode.getMessage());
    }

    public Result(StatusCode statusCode, String errorMsg) {
        this.code = statusCode.getCode();
        this.success = statusCode == StatusCode.SUCCESS;
        this.errorMsg = statusCode == StatusCode.SUCCESS ? null : errorMsg;
    }


    public Result(String errorMsg) {
        this(StatusCode.fromMessage(errorMsg), errorMsg);
    }

    public static Result ok(){
        return new Result(StatusCode.SUCCESS);
    }

    public static Result ok(Object data){
        Result result = new Result(StatusCode.SUCCESS);
        result.setData(data);
        return result;
    }

    public static Result ok(List<?> data, Long total){
        Result result = ok(data);
        result.setTotal(total);
        return result;
    }

    public static Result fail(String errorMsg){
        return new Result(errorMsg);
    }

    public static Result fail(StatusCode statusCode){
        return new Result(statusCode);
    }

    public static Result fail(StatusCode statusCode, String errorMsg){
        return new Result(statusCode, errorMsg);
    }
}
