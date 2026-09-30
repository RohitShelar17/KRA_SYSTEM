package com.company.kra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and reads access tokens, and hashes refresh tokens for storage.
 * Refresh tokens are opaque random strings; only their hash is persisted, so a
 * database leak does not hand over live sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_EMPLOYEE_ID = "eid";
    private static final String CLAIM_COMPANY_ID = "cid";
    private static final String CLAIM_NAME = "name";

    private final JwtProperties properties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getAccessTokenMinutes() * 60);
        return Jwts.builder()
                .subject(principal.getUsername())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(Map.of(
                        CLAIM_USER_ID, principal.getUserId(),
                        CLAIM_EMPLOYEE_ID, principal.getEmployeeId() == null ? 0L : principal.getEmployeeId(),
                        CLAIM_COMPANY_ID, principal.getCompanyId(),
                        CLAIM_NAME, principal.getDisplayName()))
                .signWith(signingKey())
                .compact();
    }

    public long accessTokenSeconds() {
        return properties.getAccessTokenMinutes() * 60;
    }

    public String generateRefreshToken() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    /** Refresh tokens are stored as a SHA-256 hash, never in clear text. */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public String newFamilyId() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))
                .substring(0, 32);
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object value = parse(token).get(CLAIM_USER_ID);
        return value == null ? null : Long.valueOf(value.toString());
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("Access token expired");
            return false;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Access token rejected: {}", ex.getMessage());
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
