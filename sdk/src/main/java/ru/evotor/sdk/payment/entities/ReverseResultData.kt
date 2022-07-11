package ru.evotor.sdk.payment.entities

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ReverseResultData(
    val data: ReverseBody
): Parcelable
