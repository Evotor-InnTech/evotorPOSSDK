package ru.evotor.sdk.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface RetrofitService {

    @GET("authorize")
    fun getToken(
        @Query("login") login: String,
        @Query("password") password: String
    ): Response<String>
}