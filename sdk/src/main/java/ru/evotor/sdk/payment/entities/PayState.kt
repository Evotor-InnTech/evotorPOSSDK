package ru.evotor.sdk.payment.entities

enum class PayState(val message: String) {
    CONNECTING("Подготовка к подключению"),
    TRANSACTION_STARTED("Начало транзакции"),
    PAY_FINISH("Успешный конец транзакции"),
    ANY_ERROR("Произошла ошибка"),
    DISCONNECTED("Приостановлена работа с ридером"),
    BLUETOOTH_IS_DISABLED("Ошибка при работе с Bluetooth"),
    AUTH_ERROR("Ошибка авторизации")

//    AUTH_STARTED("Начало авторизации"),
//    AUTH_SUCCESS("Успешная авторизация"),
//    AUTH_ERROR("Ошибка авторизации"),
//
//    DEVICE_PREPARE("Подготовка к подключению"),
//    DEVICE_DISCOVERY("Поиск устройства"),
//    DEVICE_CONNECTED("Устройство подключено"),
//    DEVICE_CONNECT_ERROR("Ошибка при подключении устройства"),
//
//    CARD_PAYMENT_STARTED("Начало оплаты картой"),
//    CARD_PAYMENT_SUCCESS("Оплата картой прошла успешно"),
//    CARD_PAYMENT_ERROR("При оплате картой произошла ошибка"),
//
//    CASH_PAYMENT_STARTED("Начало оплаты наличными"),
//    CASH_PAYMENT_SUCCESS("Оплата наличными прошла успешно"),
//    CASH_PAYMENT_ERROR("При оплате наличными произошла ошибка"),
//
//    GIFT_PAYMENT_STARTED("Начало оплаты ПК"),
//    GIFT_PAYMENT_SUCCESS("Оплата ПК прошла успешно"),
//    GIFT_PAYMENT_ERROR("При оплате ПК произошла ошибка"),
//
//    CARD_PAYBACK_STARTED("Начало возврата по карте"),
//    CARD_PAYBACK_SUCCESS("Возврат по карте прошел успешно"),
//    CARD_PAYBACK_ERROR("При возврате по карте произошла ошибка"),
//
//    CASH_PAYBACK_STARTED("Начало возврата наличных"),
//    CASH_PAYBACK_SUCCESS("Возврат наличных прошел успешно"),
//    CASH_PAYBACK_ERROR("При возврате наличными произошла ошибка"),
//
//    GIFT_PAYBACK_STARTED("Начало возврата ПК"),
//    GIFT_PAYBACK_SUCCESS("Возврат ПК прошел успешно"),
//    GIFT_PAYBACK_ERROR("При возврате ПК произошла ошибка"),
//
//    GIFT_REQUEST_BALANCE_STARTED("Запрос баланса ПК"),
//    GIFT_REQUEST_BALANCE_SUCCESS("Запрос баланса ПК прошел успешно"),
//    GIFT_REQUEST_BALANCE_ERROR("Запрос баланса ПК прошел с ошибкой")
}