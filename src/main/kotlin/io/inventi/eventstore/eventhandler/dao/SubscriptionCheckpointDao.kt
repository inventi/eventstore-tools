package io.inventi.eventstore.eventhandler.dao

import org.intellij.lang.annotations.Language
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface SubscriptionCheckpointDao {
    @SqlUpdate(INSERT_CATCHUP_SUBSCRIPTION)
    fun createIfNotExists(@BindBean checkpoint: SubscriptionCheckpoint): Int

    @SqlUpdate(INCREMENT_CHECKPOINT)
    fun incrementCheckpoint(@Bind("groupName") groupName: String, @Bind("streamName") streamName: String, @Bind("checkpoint") checkpoint: Long): Int

    @SqlQuery(SELECT_CURRENT_CHECKPOINT)
    fun currentCheckpoint(@Bind("groupName") groupName: String, @Bind("streamName") streamName: String): Long?

    @SqlUpdate(DELETE_CHECKPOINT)
    fun delete(@Bind("groupName") groupName: String, @Bind("streamName") streamName: String): Int

    companion object {
        private const val TABLE_NAME = "eventstore_subscription_checkpoint"

        @Language("SQL")
        private const val INSERT_CATCHUP_SUBSCRIPTION = """
            INSERT INTO $TABLE_NAME (
                group_name,
                stream_name,
                checkpoint
            )
            VALUES (
                :groupName,
                :streamName,
                :checkpoint
            )
            ON CONFLICT DO NOTHING
        """

        @Language("SQL")
        private const val INCREMENT_CHECKPOINT = """
            UPDATE $TABLE_NAME
               SET checkpoint = :checkpoint
            WHERE group_name = :groupName AND stream_name = :streamName AND coalesce(checkpoint, -1) < :checkpoint
        """

        @Language("SQL")
        private const val SELECT_CURRENT_CHECKPOINT = """
            SELECT checkpoint
            FROM $TABLE_NAME
            WHERE group_name = :groupName AND stream_name = :streamName
        """

        @Language("SQL")
        private const val DELETE_CHECKPOINT = """
            DELETE
            FROM $TABLE_NAME
            WHERE group_name = :groupName AND stream_name = :streamName
        """
    }
}