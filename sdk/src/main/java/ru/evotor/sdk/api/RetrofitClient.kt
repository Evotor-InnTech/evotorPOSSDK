package ru.evotor.sdk.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private var retrofit: Retrofit? = null

    fun getClient(url: String): Retrofit {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit ?: throw IllegalArgumentException("retrofit is not initialized")
    }
}