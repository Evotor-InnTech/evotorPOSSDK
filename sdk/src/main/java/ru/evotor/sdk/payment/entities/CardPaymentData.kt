package ru.evotor.sdk.payment.entities

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
data class CardPaymentData(
    val transactionId: String?,
    val tid: String,
    val cardNumber: String?,
    val amount: BigDecimal
): Parcelable
