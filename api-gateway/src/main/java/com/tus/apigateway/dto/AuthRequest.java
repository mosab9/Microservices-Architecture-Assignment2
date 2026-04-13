package com.tus.apigateway.dto;

public record AuthRequest(
    String username,
    String password
) {}
