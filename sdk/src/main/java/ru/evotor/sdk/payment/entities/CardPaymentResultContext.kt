package ru.evotor.sdk.payment.entities

data class CardPaymentResultContext(
    val success: Boolean,
    val message: String?,
    val code: Int?,
    val data: PaymentResultData? = null
)
