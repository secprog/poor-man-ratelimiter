package com.example.gateway.store;

import java.util.UUID;

public final class RedisKeys {
    public static final String RATE_LIMIT_RULES_HASH = "rate_limit_rules";
    public static final String RATE_LIMIT_POLICIES_HASH = "rate_limit_policies";
    public static final String SYSTEM_CONFIG_HASH = "system_config";
    public static final String ROUTE_DEFINITIONS_HASH = "gateway_route_definitions";
    public static final String POLICY_ID_SEQUENCE = "rate_limit_policy:id:seq";
    public static final String REQUEST_STATS_INDEX = "request_stats:index";
    public static final String REQUEST_STATS_PREFIX = "request_stats:";
    public static final String RATE_LIMIT_STATE_PREFIX = "rate_limit_state:";
    public static final String REQUEST_COUNTER_PREFIX = "request_counter:";
    public static final String TRAFFIC_LOG_LIST = "traffic_logs";

    private RedisKeys() {
    }

    public static String requestCounterKey(UUID ruleId, String identifier) {
        return REQUEST_COUNTER_PREFIX + ruleId + ":" + identifier;
    }

    public static String rateLimitStateKey(String limitKey) {
        return RATE_LIMIT_STATE_PREFIX + limitKey;
    }

    public static String requestStatsKey(long minuteBucket) {
        return REQUEST_STATS_PREFIX + minuteBucket;
    }
}
