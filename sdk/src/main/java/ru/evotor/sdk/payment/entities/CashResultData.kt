package ru.evotor.sdk.payment.entities

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class CashResultData(
    val data: PaymentContext,
    val transactionId: String
): Parcelable

