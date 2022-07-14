package ru.evotor.sdk.bluetooth

interface CommandsInterface {
    fun startCardPayment(json: String?, token: String)

    fun startCardRefund(json: String?, token: String)




    fun startPayment(amount: String?, json: String?)

    fun startRefund(amount: String?, json: String?)

    fun startReversal(amount: String?, json: String?)

    fun startReconciliation()

    fun cashout(cashBack: String?, json: String?)

    fun purchaseWithCashback(amount: String?, cashBack: String?, json: String?)

    fun setDefaultTerminal(isDefaultTerminal: Boolean)

    fun startServiceMenu()

    fun addTestConfiguration()
}