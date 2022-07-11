package ru.evotor.sdk.payment.entities

import com.google.gson.annotations.SerializedName

data class SendReceiptResponse(
    @SerializedName("transaction_id")
    var transactionId: String? = null
)
