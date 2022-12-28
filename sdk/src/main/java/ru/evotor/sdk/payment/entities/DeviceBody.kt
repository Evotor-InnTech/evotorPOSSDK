package ru.evotor.sdk.payment.entities

import com.google.gson.annotations.SerializedName

data class DeviceBody(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("device_model")
    val deviceModel: String,
    @SerializedName("device_name")
    val deviceName: String
)