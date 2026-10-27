-- Additive schema only; called through the isolated demo's Compose service.
CREATE TABLE IF NOT EXISTS assistant_event (
    id BIGINT NOT NULL PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    action VARCHAR(32) DEFAULT NULL,
    elapsed_ms BIGINT NOT NULL,
    alternative_groups INT NOT NULL DEFAULT 0,
    model_calls INT NOT NULL DEFAULT 0,
    model_name VARCHAR(80) DEFAULT NULL,
    usage_known BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_tokens BIGINT DEFAULT NULL,
    completion_tokens BIGINT DEFAULT NULL,
    total_tokens BIGINT DEFAULT NULL,
    KEY idx_assistant_created (created_at,stage,outcome),
    KEY idx_assistant_actor (actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
