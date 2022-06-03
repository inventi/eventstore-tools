package io.inventi.eventstore.eventhandler.dao

import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface SubscriptionInitialPositionDao {

    @SqlUpdate(INSERT_INITIAL_POSITION)
    fun createIfNotExists(@BindBean initialPosition: SubscriptionInitialPosition): Int

    @SqlQuery(SELECT_INITIAL_POSITION)
    fun initialPosition(@Bind("groupName") groupName: String, @Bind("streamName") streamName: String): Long

    companion object {
        private const val TABLE_NAME = "eventstore_subscription_initial_position"

        private const val INSERT_INITIAL_POSITION = """
            INSERT INTO $TABLE_NAME (
                group_name,
                stream_name,
                initial_position
            )
            VALUES (
                :groupName,
                :streamName,
                :initialPosition
            )
            ON CONFLICT DO NOTHING
        """

        private const val SELECT_INITIAL_POSITION = """
            SELECT initial_position
            FROM $TABLE_NAME
            WHERE group_name = :groupName AND stream_name = :streamName
        """
    }
}