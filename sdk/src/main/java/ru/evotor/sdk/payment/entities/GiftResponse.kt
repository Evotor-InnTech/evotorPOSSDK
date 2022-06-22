package ru.evotor.sdk.payment.entities

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class GiftResponse(
    val balance: BigDecimal?,
    @SerializedName("error_code")
    val errorCode: String?,
    @SerializedName("error_message")
    val errorMessage: String?
)
