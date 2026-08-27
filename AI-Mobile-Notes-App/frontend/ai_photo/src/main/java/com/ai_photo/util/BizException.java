package com.ai_photo.util;

public class BizException extends RuntimeException {
    public final int code;
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}