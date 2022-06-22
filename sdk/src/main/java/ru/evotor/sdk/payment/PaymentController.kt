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
import java.math.BigDecimal
import java.util.*

class PaymentController(context: Context) {

    private val bluetoothService: BluetoothService = BluetoothService(context)
    private val retrofitService: RetrofitService = RetrofitCommon.retrofitService

    private var token: String? = null

    private var paymentControllerListener: PaymentControllerListener? = null

    /**
     * Получение токена
     */
    fun setCredentials(
        login: String,
        password: String,
        successHandler: (String) -> Unit,
        errorHandler: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = retrofitService.getToken(login, password)
                if (response.isSuccessful) {
                    token = response.body()?.string()
                    successHandler(token.orEmpty())
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
                        if (response.isSuccessful) {
                            CoroutineScope(Dispatchers.Main).launch {
                                paymentControllerListener?.onFinished(
                                    PaymentResultContext(
                                        true,
                                        null
                                    )
                                )
                            }
                        } else {
                            throw RuntimeException(response.code().toString())
                        }
                    } catch (exception: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            paymentControllerListener?.onFinished(
                                PaymentResultContext(
                                    false,
                                    exception.message.toString()
                                )
                            )
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
    fun giftCardActivate(
        giftCardActivationContext: GiftCardActivationContext,
        paymentProductTextData: Map<String, String>? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = retrofitService.sendGift(
                    token.orEmpty(),
                    GiftActivationBody(
                        loyaltyNumber = giftCardActivationContext.loyalty_number,
                        tid = giftCardActivationContext.tid,
                        login = giftCardActivationContext.login,
                        amount = giftCardActivationContext.amount ?: BigDecimal.ZERO,
                        paymentProductTextData = paymentProductTextData
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.errorMessage != null) {
                        throw RuntimeException(body.errorMessage.toString())
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            paymentControllerListener?.onFinished(
                                PaymentResultContext(
                                    response.isSuccessful,
                                    null,
                                    GiftPaymentData(
                                        (response.body()?.transactionId ?: 0L).toString(),
                                        giftCardActivationContext.tid,
                                        giftCardActivationContext.loyalty_number
                                    )
                                )
                            )
                        }
                    }
                } else {
                    throw RuntimeException(response.code().toString())
                }
            } catch (exception: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    paymentControllerListener?.onFinished(
                        PaymentResultContext(
                            false,
                            exception.message.toString()
                        )
                    )
                }
            }
        }
    }

    /**
     * Начинает выполнение деактивации подарочной карты.
     *
     * @param giftCardActivationContext - данные для проведения деактивации подарочной карты
     */
    fun giftCardDeactivate(giftCardActivationContext: GiftCardActivationContext) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = retrofitService.cancelGift(
                    token.orEmpty(),
                    GiftCancelBody(
                        loyalty_number = giftCardActivationContext.loyalty_number,
                        tid = giftCardActivationContext.tid,
                        login = giftCardActivationContext.login,
                        transactionId = giftCardActivationContext.transactionId.orEmpty()
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.errorMessage != null) {
                        throw RuntimeException(body.errorMessage.toString())
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            paymentControllerListener?.onFinished(
                                PaymentResultContext(
                                    response.isSuccessful,
                                    null
                                )
                            )
                        }
                    }
                } else {
                    throw RuntimeException(response.code().toString())
                }
            } catch (exception: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    paymentControllerListener?.onFinished(
                        PaymentResultContext(
                            false,
                            exception.message.toString()
                        )
                    )
                }
            }
        }
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
    fun balanceInquiry(
        login: String,
        successHandler: (GiftResult) -> Unit,
        errorHandler: (String) -> Unit
    ) {
        val paymentResultListener = object : PaymentResultListener {
            override fun onResult(resultData: ResultData) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = retrofitService.getGiftBalance(
                            token.orEmpty(),
                            GiftBalanceBody(
                                loyaltyNumber = resultData.LOYALTY_NUMBER.orEmpty(),
                                tid = resultData.TID,
                                login = login
                            )
                        )
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body?.errorMessage != null) {
                                throw RuntimeException(body.errorMessage.toString())
                            } else {
                                CoroutineScope(Dispatchers.Main).launch {
                                    successHandler(
                                        GiftResult(
                                            loyaltyCardTrack = resultData.LOYALTY_NUMBER.orEmpty(),
                                            tid = resultData.TID,
                                            balance = response.body()?.balance
                                                ?: BigDecimal.ZERO
                                        )
                                    )
                                }
                            }
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
        }

        bluetoothService.setResultListener(paymentResultListener)
        bluetoothService.startPayment("1", null)
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