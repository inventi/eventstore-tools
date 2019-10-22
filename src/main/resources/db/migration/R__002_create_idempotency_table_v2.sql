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

INSERT INTO ${idempotency}_v2
(event_id, stream_name, group_name, event_type, event_stream_id, idempotency_classifier, created_at)
SELECT event_id,
       substring(idempotency_classifier, '^(.*)-' || group_name) as stream_name,
       group_name,
       substring(idempotency_classifier, '-([^-]*$)')            as event_type,
       'UNKNOWN'                                                 as event_stream_id,
       idempotency_classifier,
       created_at
FROM ${idempotency}
WHERE NOT EXISTS (SELECT * FROM ${idempotency}_v2);

CREATE UNIQUE INDEX IF NOT EXISTS idx_${idempotency}_v2_unique_idempotency_classifier ON ${idempotency}_v2 (event_id, idempotency_classifier);

ALTER TABLE ${idempotency}
    DROP CONSTRAINT IF EXISTS fk_v2_event_id;

ALTER TABLE ${idempotency}
    ADD CONSTRAINT fk_v2_event_id FOREIGN KEY (event_id, idempotency_classifier) REFERENCES ${idempotency}_v2 (event_id, idempotency_classifier)
        ON DELETE CASCADE;

