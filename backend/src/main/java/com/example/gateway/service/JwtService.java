package com.example.gateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JwtService {

    /**
     * Extracts and concatenates multiple claims from a JWT token.
     * This method does NOT validate the signature - it only parses the claims.
     * For rate limiting purposes, we trust that upstream authentication has already validated the token.
     *
     * @param token JWT token (with or without "Bearer " prefix)
     * @param claimNames List of claim names to extract (e.g., ["sub", "tenant_id", "custom_claim"])
     * @param separator String to use when concatenating multiple claims (e.g., ":")
     * @return Concatenated claim values, or null if token is invalid or claims are missing
     */
    public String extractClaims(String token, List<String> claimNames, String separator) {
        if (token == null || token.isBlank()) {
            log.debug("Token is null or empty");
            return null;
        }

        // Remove "Bearer " prefix if present
        if (token.startsWith("Bearer ") || token.startsWith("bearer ")) {
            token = token.substring(7).trim();
        }

        try {
            // Parse token without signature verification (using unsecured parser)
            // This is acceptable for rate limiting because:
            // 1. Upstream auth service has already validated the token
            // 2. We only need to read claims, not verify authenticity
            // 3. Rate limiting based on forged claims only affects the forger
            int lastDotIndex = token.lastIndexOf('.');
            if (lastDotIndex == -1) {
                log.debug("Invalid JWT format: no dots found");
                return null;
            }

            // Parse claims without validating signature
            Claims claims = Jwts.parser()
                    .unsecured()
                    .build()
                    .parseUnsecuredClaims(token)
                    .getPayload();

            // Extract specified claims
            List<String> claimValues = claimNames.stream()
                    .map(claimName -> {
                        Object claimValue = claims.get(claimName);
                        if (claimValue == null) {
                            log.debug("Claim '{}' not found in token", claimName);
                            return null;
                        }
                        return claimValue.toString();
                    })
                    .collect(Collectors.toList());

            // If any claim is missing, return null
            if (claimValues.contains(null)) {
                log.debug("One or more required claims are missing");
                return null;
            }

            // Concatenate all claim values
            String result = String.join(separator, claimValues);
            log.debug("Extracted JWT claims: {} claims concatenated with separator '{}'", claimNames.size(), separator);
            return result;

        } catch (SignatureException e) {
            // This shouldn't happen with unsecured parser, but handle it
            log.debug("JWT signature validation error (unexpected): {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("Failed to parse JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the Authorization header value from the request.
     * Returns null if header is not present.
     */
    public static String extractAuthorizationHeader(org.springframework.http.HttpHeaders headers) {
        List<String> authHeaders = headers.get("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            return null;
        }
        return authHeaders.get(0);
    }
}
