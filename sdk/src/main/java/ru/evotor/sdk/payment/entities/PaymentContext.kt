package ru.evotor.sdk.payment.entities

import ru.evotor.sdk.payment.enums.Currency
import ru.evotor.sdk.payment.enums.PaymentMethod
import java.math.BigDecimal

data class PaymentContext(
    /**
     * Сумма платежа
     */
    var amount: BigDecimal? = null,
    /**
     * Описание платежа
     */
    var description: String? = null,
    /**
     * Валюта платежа
     */
    var currency: Currency? = null,
    /**
     * Признак выполнения платежа посредством отложенной
    авторизации (только для ридеров Р17 и UROVO)
     */
    var deferred: Boolean? = null,
    /**
     * Признак подавления ожидания подписи при
    формировании и отправке чека покупателю.
    Устанавливается true в случае, если до начала платежа
    известно, что подпись не будет отправлена
     */
    var suppressSignatureWaiting: Boolean? = null,
    /**
     * Значения текстовых полей привязанного продукта, пары
    вида <код поля, значение>
     */
    var paymentProductTextData: Map<String, String>? = null,
    /**
     * Код привязанного пользовательского продукта
     */
    var paymentProductCode: String? = null,
    /**
     * ID клиентского приложения
     */
    var extID: String? = null,
    /**
     * Способ оплаты
     */
    var method: PaymentMethod? = null,
    /**
     * Код банка
     */
    var acquirerCode: String? = null,
    /**
     * Логин пользователя в системе
     */
    var login: String? = null,
    /**
     * Пароль пользователя в системе
     */
    var password: String? = null
)