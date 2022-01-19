package io.inventi.eventstore.eventhandler.dao

import org.intellij.lang.annotations.Language
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface ProcessedEventDao {
    @SqlUpdate(INSERT_PROCESSED_EVENT)
    fun save(@BindBean processedEvent: ProcessedEvent): Int

    @SqlQuery(SELECT_PROCESSED_EVENT)
    fun findBy(
            @Bind("eventId") eventId: String,
            @Bind("streamName") streamName: String,
            @Bind("groupName") groupName: String,
            @Bind("eventType") eventType: String
    ): ProcessedEvent?

    companion object {
        private const val TABLE_NAME = "eventstore_subscription_processed_event"

        @Language("SQL")
        private const val INSERT_PROCESSED_EVENT = """
            INSERT INTO $TABLE_NAME (
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

        @Language("SQL")
        private const val SELECT_PROCESSED_EVENT = """
            SELECT
                event_id,
                stream_name,
                event_stream_id,
                group_name,
                event_type,
                created_at
            FROM $TABLE_NAME
            WHERE event_id = :eventId AND
                stream_name = :streamName AND
                group_name = :groupName AND
                event_type = :eventType
        """
    }
}