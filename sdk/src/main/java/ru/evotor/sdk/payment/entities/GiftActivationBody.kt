package ru.evotor.sdk.payment.entities

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class GiftActivationBody(
    @SerializedName("loyalty_number")
    val loyaltyNumber: String,
    val tid: String,
    val login: String,
    val amount: BigDecimal,
    @SerializedName("payment_product_text_data")
    var paymentProductTextData: Map<String, String>? = null
)
