package ru.evotor.sdk.payment

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.evotor.sdk.api.RetrofitCommon
import ru.evotor.sdk.api.RetrofitService
import ru.evotor.sdk.bluetooth.BluetoothService
import ru.evotor.sdk.payment.PaymentControllerListener
import ru.evotor.sdk.payment.PaymentException
import ru.evotor.sdk.payment.entities.*
import ru.evotor.sdk.payment.enums.Currency

class PaymentController(context: Context) {

    private val bluetoothService: BluetoothService = BluetoothService(context)
    private val retrofitService: RetrofitService = RetrofitCommon.retrofitService

    private var token: String? = null

    private var paymentControllerListener: PaymentControllerListener? = null

    /**
     * Получение токена
     */
    fun setCredentials(login: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                token = retrofitService.getToken(login, password).body()?.string()
            } catch (exception: Exception) {
                //TODO Поставить обработку
            }
        }
    }

    /**
     * Начинает работу со считывателем карт
     */
    fun enable() {

    }

    /**
     * Завершает работу со считывателем карт
     */
    fun disable() {

    }

    /**
     * Задает новый обработчик событий проведения платежа
     *
     * @param paymentControllerListener - обработчик событий
     */
    fun setPaymentControllerListener(paymentControllerListener: PaymentControllerListener) {
        this.paymentControllerListener = paymentControllerListener
    }

    /**
     * Начинает выполнение платежа. При неверных параметрах
    платежа или при попытке начать новый платеж/отмену
    платежа до окончания будет сгенерировано исключение
    PaymentException
     *
     * @param paymentContext - данные платежа
     */
    @Throws(PaymentException::class)
    fun startPayment(paymentContext: PaymentContext) {
        val paymentResultListener = object : PaymentResultListener {
            override fun onResult(resultData: ResultData) {
                //TODO Метод по отправке данных чека на сервер

                //TODO После успеха...
                paymentControllerListener?.onFinished(PaymentResultContext(resultData.ERROR == "0"))
            }
        }

        bluetoothService.setResultListener(paymentResultListener)
        bluetoothService.startPayment(paymentContext.amount?.toPlainString(), null)
    }

    /**
     * Начинает выполнение отмены платежа. При попытке
    начать новый платеж/отмену платежа до окончания будет
    сгенерировано исключение PaymentException
     *
     * @param reverseContext - данные для проведения отмены/возврата
     */
    @Throws(PaymentException::class)
    fun cancelPayment(reverseContext: ReverseContext) {
        bluetoothService.startReversal(reverseContext.returnAmount?.toPlainString(), null)
    }

    /**
     * Начинает выполнение возврата платежа. При попытке
    начать новый платеж/отмену платежа до окончания будет
    сгенерировано исключение PaymentException
     *
     * @param reverseContext - данные для проведения отмены/возврата
     */
    @Throws(PaymentException::class)
    fun reversePayment(reverseContext: ReverseContext) {
        bluetoothService.startRefund(reverseContext.returnAmount?.toPlainString(), null)
    }

    /**
     * Начинает выполнение активации подарочной карты.
     *
     * @param giftCardActivationContext - данные для проведения активации подарочной карты
     */
    fun giftCardActivate(giftCardActivationContext: GiftCardActivationContext) {

    }

    /**
     * Начинает выполнение деактивации подарочной карты.
     *
     * @param giftCardActivationContext - данные для проведения деактивации подарочной карты
     */
    fun giftCardDeactivate(giftCardActivationContext: GiftCardActivationContext) {

    }

    /**
     * Начинает выполнение процедуры запроса баланса карты.
    При попытке проверить баланс до окончания
    выполняющегося платежа(отмены) будет сгенерировано
    исключение PaymentException. Асинхронный, см.
    PaymentControllerListener
     *
     * @param currency - валюта карты
     * @param acquirerCode - код банка
     */
    @Throws(PaymentException::class)
    fun balanceInquiry(currency: Currency, acquirerCode: String) {

    }

    fun getBluetoothService() = bluetoothService
}