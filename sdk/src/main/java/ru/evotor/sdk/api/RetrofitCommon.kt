package ru.evotor.sdk.api

object RetrofitCommon {
    private const val TEST_URL = "http://mvideo-app00.ev.local:8080/api/"

    val retrofitService: RetrofitService
        get() = RetrofitClient.getClient(TEST_URL).create(RetrofitService::class.java)
}