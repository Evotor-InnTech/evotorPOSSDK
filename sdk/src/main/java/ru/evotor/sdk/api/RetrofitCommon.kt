package ru.evotor.sdk.api

object RetrofitCommon {
    private const val TEST_URL = "https://mvideo.evotor.ru/api/"

    val retrofitService: RetrofitService
        get() = RetrofitClient.getClient(TEST_URL).create(RetrofitService::class.java)
}