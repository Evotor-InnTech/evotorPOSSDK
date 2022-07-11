package ru.evotor.sdk.payment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
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

    private var receiver: BroadcastReceiver? = null

    /**
     * Начинает работу со считывателем карт
     */
    fun enable(
        context: Context,
        onSuccessHandler: (BluetoothDevice) -> Unit,
        errorHandler: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            errorHandler("Необходим Manifest.permission.BLUETOOTH_CONNECT permission")
            return
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            errorHandler("Необходим Manifest.permission.BLUETOOTH_SCAN permission")
            return
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            errorHandler("Необходим Manifest.permission.ACCESS_COARSE_LOCATION permission")
            return
        }

        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && device.name == "CloudPOS") {
                            CoroutineScope(Dispatchers.IO).launch {
                                bluetoothService.selectBluetoothDevice(device.address, device)
                            }
                            onSuccessHandler(device)
                        }
                    }
                }
            }
        }

        bluetoothService.startDiscovery()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
    }

    /**
     * Завершает работу со считывателем карт
     */
    fun disable(context: Context) {
        context.unregisterReceiver(receiver)
    }

    /**
     * Начинает выполнение платежа. При неверных параметрах
    платежа или при попытке начать новый платеж/отмену
    платежа до окончания будет сгенерировано исключение
    PaymentException
     *
     * @param paymentContext - данные платежа
     * @return PaymentResultData - все данные по транзакции
     */
    @Throws(PaymentException::class)
    fun startPayment(
        paymentContext: PaymentContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        val paymentResultListener = object : PaymentResultListener {
            override fun onResult(resultData: ResultData) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (resultData.ERROR != "0") {
                            throw RuntimeException(resultData.MESSAGE)
                        }
                        val response = retrofitService.sendReceipt(
                            token.orEmpty(),
                            convertToReceipt(paymentContext, resultData)
                        )
                        if (response.isSuccessful) {
                            CoroutineScope(Dispatchers.Main).launch {
                                resultHandler(
                                    PaymentResultContext(
                                        true,
                                        null,
                                        PaymentResultData(
                                            convertToReceipt(
                                                paymentContext,
                                                resultData
                                            ), response.body()?.transactionId.orEmpty()
                                        )
                                    )
                                )
                            }
                        } else {
                            throw RuntimeException(response.code().toString())
                        }
                    } catch (exception: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            resultHandler(
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
     * Начинает выполнение возврата/отмены платежа. При попытке
    начать новый платеж/отмену платежа до окончания будет
    сгенерировано исключение PaymentException
     *
     * @param reverseContext - данные для проведения отмены/возврата
     */
    @Throws(PaymentException::class)
    fun cancelPayment(
        reverseContext: ReverseContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        val paymentResultListener = object : PaymentResultListener {
            override fun onResult(resultData: ResultData) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (resultData.ERROR != "0") {
                            throw RuntimeException(resultData.MESSAGE)
                        }
                        val response = retrofitService.reverse(
                            token.orEmpty(),
                            convertToReverse(reverseContext, resultData)
                        )
                        if (response.isSuccessful) {
                            CoroutineScope(Dispatchers.Main).launch {
                                resultHandler(
                                    PaymentResultContext(
                                        true,
                                        null,
                                        ReverseResultData(
                                            convertToReverse(
                                                reverseContext,
                                                resultData
                                            )
                                        )
                                    )
                                )
                            }
                        } else {
                            throw RuntimeException(response.code().toString())
                        }
                    } catch (exception: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            resultHandler(
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
        bluetoothService.startRefund(reverseContext.returnAmount?.toPlainString(), null)
    }

    /**
     * Начинает выполнение активации подарочной карты.
     *
     * @param giftCardActivationContext - данные для проведения активации подарочной карты
     */
    fun giftCardActivate(
        giftCardActivationContext: GiftCardActivationContext,
        paymentProductTextData: Map<String, String>? = null,
        resultHandler: (PaymentResultContext) -> Unit
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
                            resultHandler(
                                PaymentResultContext(
                                    response.isSuccessful,
                                    null,
                                    GiftResultData(
                                        (response.body()?.transactionId ?: 0L).toString(),
                                        giftCardActivationContext.tid,
                                        giftCardActivationContext.loyalty_number,
                                        giftCardActivationContext.amount ?: BigDecimal.ONE
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
                    resultHandler(
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
    fun giftCardDeactivate(
        giftCardActivationContext: GiftCardActivationContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
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
                            resultHandler(
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
                    resultHandler(
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
                                tid = resultData.TID.orEmpty(),
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
                                            tid = resultData.TID.orEmpty(),
                                            balance = response.body()?.balance ?: BigDecimal.ZERO
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

    private fun convertToReverse(reverseContext: ReverseContext, resultData: ResultData) =
        ReverseBody(
            transactionId = reverseContext.transactionID,
            amount = resultData.AMOUNT,
            currency = reverseContext.currency?.name ?: Currency.RUB.name,
            suppressSignatureWaiting = reverseContext.suppressSignatureWaiting,
            extID = reverseContext.extID,
            acquirerCode = reverseContext.acquirerCode,
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
            login = reverseContext.login,
            password = reverseContext.password
        )
}