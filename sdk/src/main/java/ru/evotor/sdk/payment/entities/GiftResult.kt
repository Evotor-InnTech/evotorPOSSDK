package ru.evotor.sdk.payment.entities

import java.math.BigDecimal

data class GiftResult(
    val loyaltyCardTrack: String,
    val tid: String,
    val balance: BigDecimal
)
