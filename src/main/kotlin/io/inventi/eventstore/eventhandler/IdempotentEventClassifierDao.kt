package io.inventi.eventstore.eventhandler

import io.inventi.eventstore.eventhandler.model.IdempotentEventClassifierRecord
import org.jdbi.v3.sqlobject.SqlObject
import org.jdbi.v3.sqlobject.transaction.Transaction

interface IdempotentEventClassifierDao : SqlObject {

    @Transaction
    fun insert(tableName: String, idempotentEventClassifierRecord: IdempotentEventClassifierRecord) {
        insert_v2(tableName, idempotentEventClassifierRecord)
        insert_v1(tableName, idempotentEventClassifierRecord)
    }

    private fun insert_v1(tableName: String, idempotentEventClassifierRecord: IdempotentEventClassifierRecord) {

        val insertQuery = """
        INSERT INTO $tableName (
            event_id,
            idempotency_classifier,
            created_at,
            group_name
        )
            VALUES (
            :eventId,
            :idempotencyClassifier,
            :createdAt,
            :groupName
            )
            """
        handle.createUpdate(insertQuery)
                .bindBean(idempotentEventClassifierRecord)
                .execute()
    }

    private fun insert_v2(tableName: String, idempotentEventClassifierRecord: IdempotentEventClassifierRecord) {

        val insertQuery = """
        INSERT INTO ${tableName}_v2 
        (
            event_id,
            stream_name,
            group_name,
            event_type,
            event_stream_id,
            idempotency_classifier,
            created_at
        )
        VALUES 
        (
            :eventId,
            :streamName,
            :groupName,
            :eventType,
            :eventStreamId,
            :idempotencyClassifier,
            :createdAt
        )
            """
        handle.createUpdate(insertQuery)
                .bindBean(idempotentEventClassifierRecord)
                .execute()
    }
}