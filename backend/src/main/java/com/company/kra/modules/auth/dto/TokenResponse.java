package com.company.kra.modules.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                            long expiresInSeconds, CurrentUserResponse user) {

    public static TokenResponse of(String accessToken, String refreshToken, long expiresInSeconds,
                                   CurrentUserResponse user) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
