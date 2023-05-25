package ru.evotor.sdk.payment

import ru.evotor.sdk.payment.entities.PayState

interface StateListener {
    fun changeState(payState: PayState, additionalMessage: String? = null)
}