package org.soft.softrear.pojo;

import org.springframework.http.HttpStatus;

import java.security.PrivateKey;

public class ResponseMessage <T>{
    private Integer code;
    private String message;
    private T data;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ResponseMessage(Integer code, String message, T data){
        this.code =code;
        this.message = message;
        this.data = data;
    }
    public static <T> ResponseMessage<T> success(T data){
        return new ResponseMessage<T>(HttpStatus.OK.value(),"success!",data);
    }
}
