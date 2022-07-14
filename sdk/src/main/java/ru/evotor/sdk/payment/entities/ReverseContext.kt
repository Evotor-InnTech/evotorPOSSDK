package ru.evotor.sdk.payment.entities

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import ru.evotor.sdk.payment.enums.Currency
import ru.evotor.sdk.payment.enums.ReverseAction
import java.math.BigDecimal

@Parcelize
data class ReverseContext(
    /**
     * ID транзакции отменяемого платежа
     */
    var transactionID: String? = null,
    /**
     * Тип отмены
     */
    var action: ReverseAction? = null,
    /**
     * Сумма, на которую будет выполнена отмена. Для полной
    отмены установить null
     */
    var returnAmount: BigDecimal? = null,
    /**
     * Валюта, используемая для отмены/возврата
     */
    var currency: Currency? = null,
    /**
     * Перечень товаров в установленном формате
     */
    var auxData: Map<String, String>? = null,
    /**
     * Телефон для отправки чека
     */
    var receiptPhone: String? = null,
    /**
     * Email для отправки чека
     */
    var receiptEmail: String? = null,
    /**
     * Признак подавления ожидания подписи при
    формировании и отправке чека покупателю.
    Устанавливается true в случае, если до начала платежа
    известно, что подпись не будет отправлена
     */
    var suppressSignatureWaiting: Boolean? = null,
    /**
     * ID клиентского приложения
     */
    var extID: String? = null,
    /**
     * Признак кредит-ваучера NFC
     */
    var nfc: Boolean? = null,
    /**
     * Информация о брэнде, маршруте и заказе
     */
    var extTranData: Map<String, String>? = null,
    /**
     * Уникальный в пределах смены номер документа (для
    операций по протоколу ТТК)
     */
    var ern: Long? = null,
    /**
     * Признак подавления фискализации операции.
    Устанавливается true в случае, если фискализация не
    требуется
     */
    var skipFiscalization: Boolean? = null,
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
): Parcelable
