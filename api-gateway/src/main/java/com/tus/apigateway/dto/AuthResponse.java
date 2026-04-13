package com.tus.apigateway.dto;

public record AuthResponse(
    String token,
    String username,
    String role,
    long expiresIn
) {}
