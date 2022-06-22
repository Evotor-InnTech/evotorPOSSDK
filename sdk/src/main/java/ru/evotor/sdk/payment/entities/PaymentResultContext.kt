package ru.evotor.sdk.payment.entities

data class PaymentResultContext(
    val success: Boolean,
    val message: String?,
    val data: Any? = null
)