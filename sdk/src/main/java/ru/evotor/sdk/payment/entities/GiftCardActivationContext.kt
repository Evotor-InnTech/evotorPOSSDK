package ru.evotor.sdk.payment.entities

import ru.evotor.sdk.payment.enums.Currency
import java.math.BigDecimal

data class GiftCardActivationContext(
    val loyalty_number: String,
    val tid: String,
    val login: String,
    val amount: BigDecimal?,
    val transactionId: String? = null
)
