package ru.evotor.sdk.payment.entities

import com.google.gson.annotations.SerializedName

data class GiftCancelBody(
    @SerializedName("loyalty_number")
    val loyalty_number: String,
    @SerializedName("tid")
    val tid: String,
    @SerializedName("login")
    val login: String,
    @SerializedName("transaction_id")
    val transactionId: String,
)
