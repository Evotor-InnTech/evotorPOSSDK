package ru.evotor.sdk.payment.entities

data class CardRefundResultContext(
    val success: Boolean,
    val message: String?,
    val code: Int?,
    val data: ReverseResultData? = null
)