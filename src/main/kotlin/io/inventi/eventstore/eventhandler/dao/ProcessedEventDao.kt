package io.inventi.eventstore.eventhandler.dao

import org.intellij.lang.annotations.Language
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface ProcessedEventDao {
    @SqlUpdate(INSERT_PROCESSED_EVENT)
    fun save(@BindBean processedEvent: ProcessedEvent): Int

    companion object {
        @Language("SQL")
        private const val INSERT_PROCESSED_EVENT = """
            INSERT INTO eventstore_subscription_processed_event (
                event_id,
                stream_name,
                event_stream_id,
                group_name,
                event_type,
                created_at
            )
            VALUES (
                :eventId,
                :streamName,
                :eventStreamId,
                :groupName,
                :eventType,
                :createdAt
            )
            ON CONFLICT DO NOTHING
        """
    }
}