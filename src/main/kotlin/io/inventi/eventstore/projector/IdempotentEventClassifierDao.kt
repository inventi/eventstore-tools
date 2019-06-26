package io.inventi.eventstore.projector

import io.inventi.eventstore.projector.model.IdempotentEventClassifierRecord
import org.jdbi.v3.sqlobject.SqlObject

interface IdempotentEventClassifierDao : SqlObject {

    fun insert(tableName: String, idempotentEventClassifierRecord: IdempotentEventClassifierRecord) {

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
}