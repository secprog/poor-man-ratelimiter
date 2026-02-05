package com.example.gateway.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for extracting field values from HTTP request bodies of various content types.
 * Supports:
 * - JSON (application/json)
 * - Form Data (application/x-www-form-urlencoded)
 * - XML (application/xml, text/xml)
 * - Multipart Form Data (multipart/form-data)
 */
@Slf4j
public class BodyFieldExtractor {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Extract a field value from request body based on content type.
     * 
     * @param bodyBytes Raw request body bytes
     * @param fieldPath Field path/name (supports dot notation for JSON: "user.id", simple names for forms: "user_id")
     * @param contentType Content-Type header value (e.g., "application/json", "application/x-www-form-urlencoded")
     * @return Extracted field value, or null if not found or parsing fails
     */
    public static String extractField(byte[] bodyBytes, String fieldPath, String contentType) {
        if (bodyBytes == null || bodyBytes.length == 0 || fieldPath == null || fieldPath.isBlank()) {
            return null;
        }
        
        // Normalize content type (remove parameters like charset)
        String normalizedContentType = contentType;
        if (contentType != null && contentType.contains(";")) {
            normalizedContentType = contentType.split(";")[0].trim().toLowerCase();
        } else if (contentType != null) {
            normalizedContentType = contentType.trim().toLowerCase();
        }
        
        try {
            if (normalizedContentType == null || normalizedContentType.contains("json")) {
                return extractFromJson(bodyBytes, fieldPath);
            } else if (normalizedContentType.contains("x-www-form-urlencoded")) {
                return extractFromFormData(bodyBytes, fieldPath);
            } else if (normalizedContentType.contains("xml")) {
                return extractFromXml(bodyBytes, fieldPath);
            } else if (normalizedContentType.contains("multipart/form-data")) {
                return extractFromMultipart(bodyBytes, fieldPath, contentType);
            } else {
                // Default to JSON for unknown content types
                log.debug("Unknown content type '{}', attempting JSON extraction", contentType);
                return extractFromJson(bodyBytes, fieldPath);
            }
        } catch (Exception e) {
            log.debug("Failed to extract field '{}' from body with content type '{}': {}", 
                    fieldPath, contentType, e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract field from JSON body using dot notation.
     */
    private static String extractFromJson(byte[] bodyBytes, String fieldPath) {
        try {
            String jsonString = new String(bodyBytes, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(jsonString);
            
            // Split path by dots and traverse
            String[] pathParts = fieldPath.split("\\.");
            JsonNode current = root;
            
            for (String part : pathParts) {
                if (current == null) {
                    return null;
                }
                current = current.get(part);
            }
            
            if (current == null) {
                return null;
            }
            
            // Convert to string
            if (current.isTextual()) {
                return current.asText();
            } else if (current.isNumber() || current.isBoolean()) {
                return current.asText();
            } else {
                return objectMapper.writeValueAsString(current);
            }
        } catch (Exception e) {
            log.debug("Failed to extract from JSON: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract field from URL-encoded form data (application/x-www-form-urlencoded).
     * Example: "username=john&api_key=abc123&email=test%40example.com"
     */
    private static String extractFromFormData(byte[] bodyBytes, String fieldName) {
        try {
            String formData = new String(bodyBytes, StandardCharsets.UTF_8);
            Map<String, String> params = parseFormData(formData);
            return params.get(fieldName);
        } catch (Exception e) {
            log.debug("Failed to extract from form data: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse URL-encoded form data into a map.
     */
    private static Map<String, String> parseFormData(String formData) {
        Map<String, String> params = new HashMap<>();
        
        if (formData == null || formData.isBlank()) {
            return params;
        }
        
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    String value = idx < pair.length() - 1 
                            ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name()) 
                            : "";
                    params.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    log.debug("Failed to decode form parameter: {}", pair);
                }
            }
        }
        
        return params;
    }
    
    /**
     * Extract field from XML body using simple XPath.
     * Field path should be XPath expression (e.g., "//user/id", "/root/api_key", "//apiKey")
     */
    private static String extractFromXml(byte[] bodyBytes, String fieldPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bodyBytes));
            
            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();
            
            // If fieldPath doesn't look like XPath, make it one
            String xpathExpression = fieldPath;
            if (!fieldPath.startsWith("/") && !fieldPath.startsWith("//")) {
                xpathExpression = "//" + fieldPath;
            }
            
            NodeList nodes = (NodeList) xpath.evaluate(xpathExpression, doc, XPathConstants.NODESET);
            
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract from XML: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract field from multipart form data.
     * This is a simplified parser - for production, consider using Apache Commons FileUpload.
     * Extracts simple text fields only, not file uploads.
     */
    private static String extractFromMultipart(byte[] bodyBytes, String fieldName, String contentType) {
        try {
            // Extract boundary from content type
            String boundary = extractBoundary(contentType);
            if (boundary == null) {
                log.debug("No boundary found in multipart content type");
                return null;
            }
            
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String[] parts = body.split("--" + boundary);
            
            for (String part : parts) {
                if (part.trim().isEmpty() || part.trim().equals("--")) {
                    continue;
                }
                
                // Parse headers and content
                String[] lines = part.split("\r?\n", 3);
                if (lines.length < 3) {
                    continue;
                }
                
                // Check if this part has the field name we're looking for
                String contentDisposition = "";
                for (String line : lines) {
                    if (line.toLowerCase().startsWith("content-disposition:")) {
                        contentDisposition = line;
                        break;
                    }
                }
                
                if (contentDisposition.contains("name=\"" + fieldName + "\"")) {
                    // Extract value (skip headers, get content)
                    int contentStart = part.indexOf("\r\n\r\n");
                    if (contentStart < 0) {
                        contentStart = part.indexOf("\n\n");
                    }
                    
                    if (contentStart >= 0) {
                        String value = part.substring(contentStart).trim();
                        // Remove trailing boundary markers
                        value = value.replaceAll("--$", "").trim();
                        return value;
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract from multipart data: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract boundary string from multipart content type.
     * Example: "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW"
     */
    private static String extractBoundary(String contentType) {
        if (contentType == null) {
            return null;
        }
        
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                return trimmed.substring("boundary=".length()).trim();
            }
        }
        
        return null;
    }
}
