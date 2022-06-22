package ru.evotor.sdk.payment.entities

import com.google.gson.annotations.SerializedName

data class GiftActivateResponse(
    @SerializedName("amount")
    val amount: Int,
    @SerializedName("error_code")
    val errorCode: Int,
    @SerializedName("error_message")
    val errorMessage: String?,
    @SerializedName("transaction_id")
    val transactionId: Long,
)