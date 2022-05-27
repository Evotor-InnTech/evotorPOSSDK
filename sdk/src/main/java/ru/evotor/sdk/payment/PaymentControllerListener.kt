package ru.evotor.sdk.payment

import ru.evotor.sdk.payment.entities.PaymentResultContext
import ru.evotor.sdk.payment.enums.ReaderEvent

interface PaymentControllerListener {
    fun onTransactionStarted(transactionId: String)

    fun onFinished(paymentResultContext: PaymentResultContext)

    fun onBatteryState(percent: Double)

    fun onReaderEvent(readerEvent: ReaderEvent, map: Map<String, String>)
}