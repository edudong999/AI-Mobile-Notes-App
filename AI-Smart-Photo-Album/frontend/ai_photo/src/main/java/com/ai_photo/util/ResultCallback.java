package com.ai_photo.util;

public interface ResultCallback<T> {
    void onSuccess(T data);
    void onError(String err);
}