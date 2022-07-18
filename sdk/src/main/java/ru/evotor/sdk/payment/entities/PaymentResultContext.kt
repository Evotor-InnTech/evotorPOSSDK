package ru.evotor.sdk.payment.entities

data class PaymentResultContext(
    val success: Boolean,
    val message: String?,
    val code: Int?,
    val data: Any? = null
)