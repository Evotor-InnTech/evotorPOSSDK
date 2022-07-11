package ru.evotor.sdk.payment.entities

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ReverseBody(
    @SerializedName("transaction_id")
    var transactionId: String? = null,
    var amount: String? = null,
    var currency: String? = null,
    @SerializedName("suppress_sign")
    var suppressSignatureWaiting: Boolean? = null,
    @SerializedName("ext_id")
    var extID: String? = null,
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
): Parcelable
