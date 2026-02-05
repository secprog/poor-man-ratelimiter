package com.example.gateway.controller;

import com.example.gateway.filter.AntiBotFilter;
import com.example.gateway.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TokenController {

    private final AntiBotFilter antiBotFilter;
    private final ConfigurationService configService;

    /**
     * Generate a new one-time form token for anti-bot protection.
     * Clients should request this before displaying a form and include
     * it in the form submission.
     */
    @GetMapping("/form")
    public Mono<Map<String, Object>> generateFormToken() {
        String token = antiBotFilter.generateFormToken();
        long loadTime = System.currentTimeMillis();
        String honeypotField = configService.getConfig("antibot-honeypot-field", "_hp_email");

        return Mono.just(Map.of(
                "token", token,
                "loadTime", loadTime,
                "honeypotField", honeypotField,
                "expiresIn", 600 // Token expires in 10 minutes (600 seconds)
        ));
    }

    /**
     * Generate an anti-bot challenge response.
     * Returns either HTML meta refresh challenge or a token for JavaScript-based challenges.
     * Challenge type is determined by configuration.
     */
    @GetMapping("/challenge")
    public Mono<org.springframework.http.ResponseEntity<String>> generateChallenge(ServerWebExchange exchange) {
        String challengeType = configService.getConfig("antibot-challenge-type", "metarefresh");
        
        if ("metarefresh".equalsIgnoreCase(challengeType)) {
            return generateMetaRefreshChallenge(exchange);
        } else {
            // Default: return token for JavaScript challenge
            return generateTokenChallenge();
        }
    }

    /**
     * Generate a meta refresh challenge - simple HTML that auto-reloads after delay.
     * No JavaScript required, works on all browsers.
     */
    private Mono<org.springframework.http.ResponseEntity<String>> generateMetaRefreshChallenge(ServerWebExchange exchange) {
        String token = antiBotFilter.generateFormToken();
        long delay = configService.getLong("antibot-metarefresh-delay", 3);
        
        // Build HTML response with meta refresh
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta http-equiv=\"refresh\" content=\"" + delay + "; url=" + exchange.getRequest().getURI().getPath() + "\">\n" +
                "  <title>Please wait...</title>\n" +
                "  <style>\n" +
                "    body { font-family: Arial, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; background: #f5f5f5; margin: 0; }\n" +
                "    .container { text-align: center; background: white; padding: 40px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n" +
                "    h1 { color: #333; font-size: 24px; margin: 0 0 20px 0; }\n" +
                "    p { color: #666; font-size: 16px; margin: 0 0 20px 0; }\n" +
                "    .spinner { border: 4px solid #f3f3f3; border-top: 4px solid #3498db; border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite; margin: 20px auto; }\n" +
                "    @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"container\">\n" +
                "    <h1>Verifying your browser...</h1>\n" +
                "    <div class=\"spinner\"></div>\n" +
                "    <p>This page will automatically refresh in " + delay + " seconds.</p>\n" +
                "    <p style=\"font-size: 12px; color: #999; margin-top: 30px;\">If you are not redirected, <a href=\"javascript:location.reload()\">click here</a>.</p>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
        
        // Set form token in cookie for the next request to verify
        exchange.getResponse().getCookies().add("X-Form-Token-Challenge", 
            ResponseCookie.from("X-Form-Token-Challenge", token)
                .path("/")
                .maxAge(600) // 10 minutes
                .build());
        
        return Mono.just(org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html));
    }

    /**
     * Generate a token for JavaScript-based challenges.
     * Client can use this to defer or handle challenges programmatically.
     */
    private Mono<org.springframework.http.ResponseEntity<String>> generateTokenChallenge() {
        String token = antiBotFilter.generateFormToken();
        return Mono.just(org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"token\":\"" + token + "\"}"));
    }
}
