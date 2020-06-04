package com.blog.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一结果封装
 * @author SZ-UserBDG7
 */
@Data
public class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private String code;
    private String msg;
    private Object data;

    public static Result ok(){
        Result result = new Result();
        result.setCode("20000");
        result.setMsg("操作成功");
        return result;
    }
    public static Result successful(String msg,Object data){
        Result result = new Result();
        result.setCode("20001");
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
    public static Result failed(){
        Result result = new Result();
        result.setCode("40000");
        result.setMsg("操作失败");
        return result;
    }
    public static Result failed(String msg,Object data){
        Result result = new Result();
        result.setCode("40001");
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}
