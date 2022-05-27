package ru.evotor.sdk.payment.entities

import ru.evotor.sdk.payment.enums.Currency
import java.math.BigDecimal

data class GiftCardActivationContext(
    var activate: Boolean? = null,
    var acquirerCode: String? = null,
    var amount: BigDecimal? = null,
    var currency: Currency? = null,
    var activationId: Long? = null
)
