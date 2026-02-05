package com.example.gateway.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for extracting values from JSON objects using simple dot-notation paths.
 * Supports nested paths like "user.id", "account.settings.theme", etc.
 */
@Slf4j
public class JsonPathExtractor {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Extract a value from a JSON string using dot notation path.
     * 
     * @param jsonString JSON string to parse
     * @param fieldPath Dot-notation path (e.g., "user.id", "api_key", "settings.limits.max")
     * @return Extracted value as String, or null if not found or parsing fails
     */
    public static String extractField(String jsonString, String fieldPath) {
        if (jsonString == null || jsonString.isBlank() || fieldPath == null || fieldPath.isBlank()) {
            return null;
        }
        
        try {
            // Parse JSON
            JsonNode root = objectMapper.readTree(jsonString);
            
            // Split path by dots and traverse the tree
            String[] pathParts = fieldPath.split("\\.");
            JsonNode current = root;
            
            for (String part : pathParts) {
                if (current == null) {
                    log.debug("Path traversal failed: '{}' does not exist in JSON", fieldPath);
                    return null;
                }
                
                current = current.get(part);
            }
            
            // If the final value is null, return null
            if (current == null) {
                log.debug("Field '{}' not found in JSON", fieldPath);
                return null;
            }
            
            // Convert to string
            if (current.isTextual()) {
                return current.asText();
            } else if (current.isNumber()) {
                return current.asText();
            } else if (current.isBoolean()) {
                return current.asBoolean() ? "true" : "false";
            } else {
                // For complex objects/arrays, convert to JSON string
                return objectMapper.writeValueAsString(current);
            }
            
        } catch (Exception e) {
            log.debug("Failed to extract field '{}' from JSON: {}", fieldPath, e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract a field from raw HTTP body bytes.
     * 
     * @param bodyBytes Raw request body bytes
     * @param fieldPath Dot-notation path
     * @return Extracted value as String, or null if not found or not valid JSON
     */
    public static String extractField(byte[] bodyBytes, String fieldPath) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return null;
        }
        
        try {
            String jsonString = new String(bodyBytes);
            return extractField(jsonString, fieldPath);
        } catch (Exception e) {
            log.debug("Failed to extract field from body bytes: {}", e.getMessage());
            return null;
        }
    }
}
