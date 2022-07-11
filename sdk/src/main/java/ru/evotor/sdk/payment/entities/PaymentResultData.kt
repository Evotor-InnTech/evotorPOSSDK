package ru.evotor.sdk.payment.entities

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class PaymentResultData(
    val data: ReceiptBody,
    val transactionId: String
): Parcelable
