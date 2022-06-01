package ru.evotor.sdk.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*
import ru.evotor.sdk.payment.entities.ReceiptBody

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
    ): Response<ResponseBody>
}