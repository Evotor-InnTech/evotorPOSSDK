package ru.evotor.sdk.payment

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.evotor.sdk.api.RetrofitCommon
import ru.evotor.sdk.api.RetrofitService
import ru.evotor.sdk.bluetooth.BluetoothService
import ru.evotor.sdk.payment.entities.*
import ru.evotor.sdk.payment.enums.Currency
import java.util.*

class PaymentController(context: Context) {

    private val bluetoothService: BluetoothService = BluetoothService(context)
    private val retrofitService: RetrofitService = RetrofitCommon.retrofitService

    private var token: String? = null

    private var paymentControllerListener: PaymentControllerListener? = null

    /**
     * Получение токена
     */
    fun setCredentials(login: String, password: String, errorHandler: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = retrofitService.getToken(login, password)
                if (response.isSuccessful) {
                    token = response.body()?.string()
                } else {
                    throw RuntimeException(response.code().toString())
                }
            } catch (exception: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    errorHandler(exception.message.toString())
                }
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
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = retrofitService.sendReceipt(
                            token.orEmpty(),
                            convertToReceipt(paymentContext, resultData)
                        )
                        CoroutineScope(Dispatchers.Main).launch {
                            paymentControllerListener?.onFinished(PaymentResultContext(response.isSuccessful))
                        }
                    } catch (exception: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            paymentControllerListener?.onFinished(PaymentResultContext(false))
                        }
                    }
                }
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

    private fun convertToReceipt(paymentContext: PaymentContext, resultData: ResultData) =
        ReceiptBody(
            amount = resultData.AMOUNT,
            description = paymentContext.description,
            currency = paymentContext.currency?.name ?: Currency.RUB.name,
            suppressSignatureWaiting = paymentContext.suppressSignatureWaiting,
            paymentProductTextData = paymentContext.paymentProductTextData,
            paymentProductCode = paymentContext.paymentProductCode,
            extID = paymentContext.extID,
            method = paymentContext.method?.name,
            acquirerCode = paymentContext.acquirerCode,
            mid = resultData.MID,
            pan = resultData.PAN,
            hash = resultData.HASH,
            requestId = resultData.REQUEST_ID,
            tsn = resultData.TSN,
            time = resultData.TIME,
            rrn = resultData.RRN,
            hashAlgo = resultData.HASH_ALGO,
            isOwn = resultData.IS_OWN,
            cardName = resultData.CARD_NAME,
            date = resultData.DATE,
            tid = resultData.TID,
            amountClear = resultData.AMOUNT_C,
            encryptedData = resultData.ENCRYPTED_DATA,
            holderName = resultData.HOLDENAME,
            flags = resultData.FLAGS,
            expDate = resultData.EXP_DATE,
            lltId = resultData.LLT_ID,
            authCode = resultData.AUTH_CODE,
            message = resultData.MESSAGE,
            pilOfType = resultData.PIL_OP_TYPE,
            error = resultData.ERROR,
            cardId = resultData.CARD_ID,
            login = paymentContext.login,
            password = paymentContext.password
        )
}