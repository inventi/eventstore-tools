CREATE TABLE IF NOT EXISTS ${idempotency}_v2
(
    event_id               text,
    stream_name            text,
    group_name             text,
    event_type             text,
    event_stream_id        text,
    idempotency_classifier text,
    primary key (event_id, stream_name, group_name, event_type),
    created_at             timestamp
);

ALTER TABLE IF EXISTS ${idempotency} DROP CONSTRAINT IF EXISTS fk_v2_event_id;
DROP TABLE IF EXISTS ${idempotency};

DROP INDEX IF EXISTS idx_${idempotency}_v2_unique_idempotency_classifier;
ALTER TABLE IF EXISTS ${idempotency}_v2 DROP COLUMN IF EXISTS idempotency_classifier;
ALTER TABLE IF EXISTS ${idempotency}_v2 RENAME CONSTRAINT ${idempotency}_v2_pkey TO eventstore_subscription_processed_event_pkey;
ALTER TABLE IF EXISTS ${idempotency}_v2 RENAME TO eventstore_subscription_processed_event;
