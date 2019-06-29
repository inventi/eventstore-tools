package io.inventi.eventstore.eventhandler

import io.inventi.eventstore.eventhandler.model.IdempotentEventClassifierRecord
import org.jdbi.v3.sqlobject.SqlObject

interface IdempotentEventClassifierDao : SqlObject {

    fun insert(tableName: String, idempotentEventClassifierRecord: IdempotentEventClassifierRecord) {

        val insertQuery = """
        INSERT INTO  (
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
}