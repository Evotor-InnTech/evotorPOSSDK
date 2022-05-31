package ru.evotor.sdk.payment

import ru.evotor.sdk.payment.entities.ResultData

interface PaymentResultListener {
    fun onResult(resultData: ResultData)
}