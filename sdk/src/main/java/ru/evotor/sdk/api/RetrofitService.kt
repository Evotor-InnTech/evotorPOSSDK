package ru.evotor.sdk.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface RetrofitService {

    @GET("authorize")
    suspend fun getToken(
        @Query("login") login: String,
        @Query("password") password: String
    ): Response<ResponseBody>
}