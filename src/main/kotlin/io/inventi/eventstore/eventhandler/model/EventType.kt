package io.inventi.eventstore.eventhandler.model

enum class EventType(val type: String) {
    V2_INVOICE("v2.Invoice"),
    V2_INVOICE_NOTIFICATION("v2.Invoice.Notification"),
    V2_FACTOR_INVOICE("v2.FactorInvoice"),
    V3_PAYOUT("v3.Payout"),
    V3_PAYMENT_RECEIPT("v3.PaymentReceipt"),
    V3_CONTRACT("v3.Contract")
}