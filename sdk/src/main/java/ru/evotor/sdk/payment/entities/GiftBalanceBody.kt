package ru.evotor.sdk.payment.entities

import com.google.gson.annotations.SerializedName

data class GiftBalanceBody(
    @SerializedName("loyalty_number")
    val loyaltyNumber: String,
    val tid: String,
    val login: String
)