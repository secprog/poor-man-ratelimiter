package com.example.gateway.controller;

import com.example.gateway.model.RateLimitPolicy;
import com.example.gateway.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Allow frontend access
public class AdminController {
    private final PolicyService policyService;

    @GetMapping
    public Flux<RateLimitPolicy> getAll() {
        return policyService.getAllPolicies();
    }

    @PostMapping
    public Mono<RateLimitPolicy> create(@RequestBody RateLimitPolicy policy) {
        return policyService.createPolicy(policy);
    }

    @PutMapping("/{id}")
    public Mono<RateLimitPolicy> update(@PathVariable Long id, @RequestBody RateLimitPolicy policy) {
        return policyService.updatePolicy(id, policy);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable Long id) {
        return policyService.deletePolicy(id);
    }
}
