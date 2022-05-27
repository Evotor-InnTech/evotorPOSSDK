package ru.evotor.sdk.payment.enums

/**
 * Набор возможных способов выполнения платежа
 */
enum class PaymentMethod {
    /**
     * Платежной картой
     */
    CARD,

    /**
     * Наличные
     */
    CASH,

    /**
     * Кредитом
     */
    CREDIT,

    /**
     * Привязанной картой
     */
    LINKED_CARD,

    /**
     * По ссылке
     */
    OTHER,

    /**
     * Внешним POS-терминалом
     */
    OUTER_CARD,

    /**
     * Предоплатой
     */
    PREPAID
}