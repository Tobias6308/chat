package com.chat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class AdminJwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(AdminJwtUtil.class);

    @Value("${admin-jwt.secret}")
    private String secret;

    @Value("${admin-jwt.expiration}")
    private Long expiration;

    public String generateToken(String adminId) {
        if (secret == null || secret.isEmpty()) {
            logger.error("generateToken FAILED - secret is NULL or EMPTY!");
            throw new IllegalStateException("Admin JWT secret is not configured!");
        }
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(adminId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public String parseToken(String token) {
        try {
            logger.debug("Parsing token, length: {}", token.length());
            
            Claims claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
            
            String subject = claims.getSubject();
            logger.debug("Token parsed successfully - adminId: {}", subject);
            
            return subject;
        } catch (ExpiredJwtException e) {
            logger.warn("Admin JWT token EXPIRED: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.warn("Admin JWT token MALFORMED: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.warn("Admin JWT token UNSUPPORTED: {}", e.getMessage());
        } catch (SignatureException e) {
            logger.warn("Admin JWT token SIGNATURE INVALID: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Admin JWT token ILLEGAL ARGUMENT: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Admin JWT token PARSE ERROR: {}", e.getMessage());
        }
        return null;
    }

    public Map<String, Object> parseTokenInfo(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
            Map<String, Object> result = new HashMap<>();
            result.put("adminId", claims.getSubject());
            return result;
        } catch (Exception e) {
            logger.warn("Admin JWT token parse error: {}", e.getMessage());
        }
        return null;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            logger.warn("Admin JWT token validation failed: {}", e.getMessage());
        }
        return false;
    }

    public boolean validateToken(String token, String adminId) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject().equals(adminId);
        } catch (Exception e) {
            logger.warn("Admin JWT token validation failed: {}", e.getMessage());
        }
        return false;
    }
}