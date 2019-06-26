package io.inventi.eventstore.projector

import io.inventi.eventstore.projector.model.IdempotentEventClassifierRecord
import org.jdbi.v3.sqlobject.SqlObject

interface IdempotentEventClassifierDao : SqlObject {

    fun insert(tableName: String, idempotentEventClassifierRecord: IdempotentEventClassifierRecord) {

        val insertQuery = """
        INSERT INTO $tableName (
            id,
            idempotency_classifier,
            created_at
        )
            VALUES (
            :id,
            :idempotencyClassifier,
            :createdAt
            )
            """
        handle.createUpdate(insertQuery)
                .bindBean(idempotentEventClassifierRecord)
                .execute()
    }
}