package io.inventi.eventstore.eventhandler.util

import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate


class ExecutingTransactionTemplate : TransactionTemplate() {
    override fun <T : Any?> execute(action: TransactionCallback<T>): T? {
        return action.doInTransaction(IrrelevantStatus())
    }

    private class IrrelevantStatus : TransactionStatus {
        override fun isRollbackOnly(): Boolean {
            TODO("not implemented")
        }

        override fun isNewTransaction(): Boolean {
            TODO("not implemented")
        }

        override fun setRollbackOnly() {
            TODO("not implemented")
        }

        override fun hasSavepoint(): Boolean {
            TODO("not implemented")
        }

        override fun isCompleted(): Boolean {
            TODO("not implemented")
        }

        override fun rollbackToSavepoint(savepoint: Any) {
            TODO("not implemented")
        }

        override fun flush() {
            TODO("not implemented")
        }

        override fun createSavepoint(): Any {
            TODO("not implemented")
        }

        override fun releaseSavepoint(savepoint: Any) {
            TODO("not implemented")
        }
    }
}