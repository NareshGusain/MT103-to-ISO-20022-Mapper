-- Flyway Migration V2: Create address_suggestions table for AI / Rule-based address enrichment
CREATE TABLE address_suggestions (
    id UUID PRIMARY KEY,
    conversion_id UUID NOT NULL,
    party_type VARCHAR(16) NOT NULL,
    original_address TEXT NOT NULL,
    street VARCHAR(256),
    street_confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    town VARCHAR(256),
    town_confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    post_code VARCHAR(64),
    post_code_confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    country VARCHAR(64),
    country_confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    provider VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    decided_by VARCHAR(64),
    decision_note TEXT,
    CONSTRAINT fk_suggestions_conversion FOREIGN KEY (conversion_id) REFERENCES conversions(id) ON DELETE CASCADE
);

CREATE INDEX idx_suggestions_conversion_id ON address_suggestions(conversion_id);
CREATE INDEX idx_suggestions_status ON address_suggestions(status);

