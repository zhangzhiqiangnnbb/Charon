package com.Charon.dto;

public record AuthResponse(
    String token,
    String type,
    long expiresIn
) {}
