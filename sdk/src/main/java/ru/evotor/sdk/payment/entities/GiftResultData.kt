package ru.evotor.sdk.payment.entities

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
data class GiftResultData(
    val transactionId: String?,
    val tid: String,
    val loyalty_number: String,
    val balance: BigDecimal
): Parcelable
