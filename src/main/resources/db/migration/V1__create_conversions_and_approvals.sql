-- Flyway Migration V1: Create conversions and approvals audit tables
-- Complies with PostgreSQL and H2 (PostgreSQL compatibility mode)

CREATE TABLE conversions (
    id UUID PRIMARY KEY,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    input_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    pacs_xml TEXT,
    report_json TEXT,
    tool_version VARCHAR(32) NOT NULL
);

CREATE INDEX idx_conversions_input_hash ON conversions(input_hash);
CREATE INDEX idx_conversions_received_at ON conversions(received_at);

CREATE TABLE approvals (
    id UUID PRIMARY KEY,
    conversion_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    note TEXT,
    CONSTRAINT fk_approvals_conversion FOREIGN KEY (conversion_id) REFERENCES conversions(id) ON DELETE CASCADE
);

CREATE INDEX idx_approvals_conversion_id ON approvals(conversion_id);

