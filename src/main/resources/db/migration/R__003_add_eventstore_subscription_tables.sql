CREATE TABLE IF NOT EXISTS eventstore_subscription_checkpoint (
    group_name             text,
    stream_name            text,
    checkpoint             bigint,
    primary key (group_name, stream_name)
);

-- This is used by spring integration for leader election lock registry. Sourced from org/springframework/integration/jdbc/schema-postgresql.sql
CREATE TABLE IF NOT EXISTS EVENTSTORE_SUBSCRIPTION_LOCK (
	LOCK_KEY CHAR(36) NOT NULL,
	REGION VARCHAR(100) NOT NULL,
	CLIENT_ID CHAR(36),
	CREATED_DATE TIMESTAMP NOT NULL,
	constraint INT_LOCK_PK primary key (LOCK_KEY, REGION)
);