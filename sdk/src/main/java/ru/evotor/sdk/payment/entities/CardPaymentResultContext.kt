package ru.evotor.sdk.payment.entities

data class CardPaymentResultContext(
    val success: Boolean,
    val message: String?,
    val data: PaymentResultData? = null
)
