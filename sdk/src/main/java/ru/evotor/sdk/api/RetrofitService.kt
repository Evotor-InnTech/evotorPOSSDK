package ru.evotor.sdk.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*
import ru.evotor.sdk.payment.entities.*

interface RetrofitService {

    @GET("authorize")
    suspend fun getToken(
        @Query("login") login: String,
        @Query("password") password: String
    ): Response<ResponseBody>

    @POST("transaction")
    suspend fun sendReceipt(
        @Header("Authorization") token: String,
        @Body receiptBody: ReceiptBody
    ): Response<SendReceiptResponse>

    @POST("transaction/reversal")
    suspend fun reverse(
        @Header("Authorization") token: String,
        @Body reverseBody: ReverseBody
    ): Response<ResponseBody>

    @POST("giftcard/balance")
    suspend fun getGiftBalance(
        @Header("Authorization") token: String,
        @Body giftBody: GiftBalanceBody
    ): Response<GiftResponse>

    @POST("giftcard/authorize")
    suspend fun sendGift(
        @Header("Authorization") token: String,
        @Body giftBody: GiftActivationBody
    ): Response<GiftActivateResponse>

    @POST("giftcard/cancel")
    suspend fun cancelGift(
        @Header("Authorization") token: String,
        @Body cancelGiftBody: GiftCancelBody
    ): Response<GiftActivateResponse>
}