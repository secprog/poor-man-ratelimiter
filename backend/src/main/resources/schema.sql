CREATE TABLE IF NOT EXISTS rate_limit_policies (
    policy_id SERIAL PRIMARY KEY,
    route_pattern VARCHAR(255) NOT NULL,
    limit_type VARCHAR(50) NOT NULL,
    replenish_rate INTEGER NOT NULL,
    burst_capacity INTEGER NOT NULL,
    requested_tokens INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    header_name VARCHAR(255),
    session_cookie_name VARCHAR(255),
    trust_proxy BOOLEAN,
    CONSTRAINT unique_policy_route_type UNIQUE (route_pattern, limit_type)
);

CREATE TABLE IF NOT EXISTS rate_limit_rules (
    id UUID PRIMARY KEY,
    path_pattern VARCHAR(255) NOT NULL,
    allowed_requests INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0,
    queue_enabled BOOLEAN DEFAULT FALSE,
    max_queue_size INT DEFAULT 0,
    delay_per_request_ms INT DEFAULT 100,
    -- JWT-based rate limiting fields
    jwt_enabled BOOLEAN DEFAULT FALSE,
    jwt_claims TEXT,  -- JSON array of claim names to concatenate (e.g., ["sub", "tenant_id"])
    jwt_claim_separator VARCHAR(10) DEFAULT ':',  -- Separator for concatenating multiple claims
    -- Body-based rate limiting fields
    body_limit_enabled BOOLEAN DEFAULT FALSE,
    body_field_path VARCHAR(255),  -- JSONPath or simple field name (e.g., "user_id", "api_key", "user.id")
    body_limit_type VARCHAR(20) DEFAULT 'replace_ip',  -- "replace_ip" or "combine_with_ip"
    body_content_type VARCHAR(100) DEFAULT 'application/json',  -- Expected content type: json, form, xml, multipart
    -- Header-based rate limiting fields
    header_limit_enabled BOOLEAN DEFAULT FALSE,
    header_name VARCHAR(255),  -- Header name to extract value from (e.g., "X-API-Key", "X-User-Id")
    header_limit_type VARCHAR(20) DEFAULT 'replace_ip',  -- "replace_ip" or "combine_with_ip"
    -- Cookie-based rate limiting fields
    cookie_limit_enabled BOOLEAN DEFAULT FALSE,
    cookie_name VARCHAR(255),  -- Cookie name to extract value from (e.g., "session_id", "user_token")
    cookie_limit_type VARCHAR(20) DEFAULT 'replace_ip'  -- "replace_ip" or "combine_with_ip"
);

CREATE TABLE IF NOT EXISTS rate_limit_state (
    limit_key VARCHAR(255) PRIMARY KEY,
    remaining_tokens INTEGER NOT NULL,
    last_refill_time TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS system_config (
    config_key VARCHAR(255) PRIMARY KEY,
    config_value TEXT
);

-- Default Settings
INSERT INTO system_config (config_key, config_value) VALUES
('ip-header-name', 'X-Forwarded-For'),
('trust-x-forwarded-for', 'false'),
('antibot-enabled', 'true'),
('antibot-min-submit-time', '2000'),
('antibot-honeypot-field', '_hp_email'),
('session-cookie-name', 'JSESSIONID'),
('antibot-challenge-type', 'metarefresh'),
('antibot-metarefresh-delay', '3'),
('antibot-preact-difficulty', '1')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS request_stats (
    id SERIAL PRIMARY KEY,
    time_window TIMESTAMP NOT NULL,
    allowed_count BIGINT DEFAULT 0,
    blocked_count BIGINT DEFAULT 0,
    CONSTRAINT unique_time_window UNIQUE (time_window)
);

CREATE TABLE IF NOT EXISTS traffic_logs (
    id UUID PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    path VARCHAR(255) NOT NULL,
    client_ip VARCHAR(45) NOT NULL,
    status_code INTEGER NOT NULL,
    allowed BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS request_counters (
    rule_id UUID NOT NULL,
    client_ip VARCHAR(45) NOT NULL,
    request_count INTEGER NOT NULL,
    window_start TIMESTAMP NOT NULL,
    PRIMARY KEY (rule_id, client_ip)
);

-- Insert a default policy for testing
INSERT INTO rate_limit_policies (route_pattern, limit_type, replenish_rate, burst_capacity, requested_tokens)
VALUES ('/**', 'IP_BASED', 10, 20, 1)
ON CONFLICT DO NOTHING;

-- Insert a default rate limit rule for testing (100 requests per 60 seconds)
INSERT INTO rate_limit_rules (id, path_pattern, allowed_requests, window_seconds, active, queue_enabled, max_queue_size, delay_per_request_ms)
VALUES ('00000000-0000-0000-0000-000000000001', '/**', 100, 60, true, false, 0, 100)
ON CONFLICT DO NOTHING;
