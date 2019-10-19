CREATE TABLE IF NOT EXISTS ${idempotency}_v2
(
    event_id        text,
    stream_name     text,
    group_name      text,
    event_type      text,
    event_stream_id text,
    primary key (event_id, stream_name, group_name, event_type),
    created_at      timestamp
);

INSERT INTO ${idempotency}_v2
(event_id, stream_name, group_name, event_type, event_stream_id, created_at)
SELECT event_id,
       substring(idempotency_classifier, '^(.*)-' || group_name) as stream_name,
       group_name,
       substring(idempotency_classifier, '-([^-]*$)')            as event_type,
       'UNKNOWN'                                                 as event_stream_id,
       created_at
FROM ${idempotency};


