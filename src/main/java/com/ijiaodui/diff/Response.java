package com.ijiaodui.diff;

public class Response {
    private int code;
    private String msg;

    public Response(int code, String message) {
        this.code = code;
        this.msg = message;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
