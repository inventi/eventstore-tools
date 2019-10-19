create table IF NOT EXISTS ${idempotency}
(
    event_id text,
    idempotency_classifier text,
    primary key (event_id, idempotency_classifier),
    group_name text,
    created_at timestamp
);