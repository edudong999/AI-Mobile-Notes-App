package com.ai_photo.util;

/** Sealed-style result type for repository calls. */
public abstract class Result<T> {
    private Result() {}

    public static final class Success<T> extends Result<T> {
        public final T data;
        public Success(T data) { this.data = data; }
    }
    public static final class Error<T> extends Result<T> {
        public final int code;
        public final String message;
        public Error(int code, String message) { this.code = code; this.message = message; }
    }
    public static final class Network<T> extends Result<T> {
        public final Throwable cause;
        public Network(Throwable cause) { this.cause = cause; }
    }

    public static <T> Result<T> ok(T data) { return new Success<>(data); }
    public static <T> Result<T> err(int code, String msg) { return new Error<>(code, msg); }
    public static <T> Result<T> net(Throwable t) { return new Network<>(t); }
}