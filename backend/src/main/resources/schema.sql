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
    active BOOLEAN DEFAULT true
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
('antibot-metarefresh-delay', '3')
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
