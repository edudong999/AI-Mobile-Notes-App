package com.ai_photo.data.model.auth;

public class RegisterRequest {
    public String username;
    public String password;
    public String email;
}

public class LoginRequest {
    public String username;
    public String password;
}

public class AuthResponse {
    public long userId;
    public String username;
    public String token;
    public int expiresIn;
}