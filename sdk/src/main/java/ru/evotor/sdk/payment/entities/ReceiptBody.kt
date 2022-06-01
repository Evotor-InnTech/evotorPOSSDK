package ru.evotor.sdk.payment.entities

import com.google.gson.annotations.SerializedName
import ru.evotor.sdk.payment.enums.Currency
import ru.evotor.sdk.payment.enums.PaymentMethod
import java.math.BigDecimal

data class ReceiptBody(
    var amount: String? = null,
    var description: String? = null,
    var currency: String? = null,
    @SerializedName("suppress_sign")
    var suppressSignatureWaiting: Boolean? = null,
    @SerializedName("payment_product_text_data")
    var paymentProductTextData: Map<String, String>? = null,
    @SerializedName("payment_product_code")
    var paymentProductCode: String? = null,
    @SerializedName("ext_id")
    var extID: String? = null,
    @SerializedName("method")
    var method: String? = null,
    @SerializedName("acquirer_code")
    var acquirerCode: String? = null,
    val mid: String? = null,
    val pan: String? = null,
    val hash: String? = null,
    @SerializedName("request_id")
    val requestId: String? = null,
    val tsn: String? = null,
    val time: String? = null,
    val rrn: String? = null,
    @SerializedName("hash_algo")
    val hashAlgo: String? = null,
    @SerializedName("is_own")
    val isOwn: String? = null,
    @SerializedName("card_type")
    val cardName: String? = null,
    val date: String? = null,
    val tid: String? = null,
    @SerializedName("amount_clear")
    val amountClear: String? = null,
    @SerializedName("encrypted_data")
    val encryptedData: String? = null,
    @SerializedName("holder_name")
    val holderName: String? = null,
    @SerializedName("flags")
    val flags: String? = null,
    @SerializedName("exp_date")
    val expDate: String? = null,
    @SerializedName("llt_id")
    val lltId: String? = null,
    @SerializedName("auth_code")
    val authCode: String? = null,
    val message: String? = null,
    @SerializedName("pil_op_type")
    val pilOfType: String? = null,
    val error: String? = null,
    @SerializedName("card_id")
    val cardId: String? = null,
    val login: String? = null,
    val password: String? = null
)