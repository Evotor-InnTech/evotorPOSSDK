package ru.evotor.sdk.payment.enums

/**
 * Набор возможных способов отмены платежа
 */
enum class ReverseAction(value: String) {
    /**
     * Отмена платежа
     */
    CANCEL("Cancel"),

    /**
     * Возврат платежа
     */
    RETURN("Return")
}