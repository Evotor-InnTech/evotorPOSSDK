package ru.evotor.sdk.bluetooth

enum class BluetoothCommand {
    /**
     * Включение/выключение режима тестового терминала
     */
    SET_DEFAULT_TERMINAL,
    /**
     * Данная функция предназначена для выполнения операций - Оплата
     */
    START_PAYMENT,
    /**
     * Данная функция предназначена для выполнения операций - Возврат
     */
    START_REFUND,
    /**
     * Данная функция предназначена для выполнения операций - Отмена
     */
    START_REVERSAL,
    /**
     * Данная функция предназначена для выполнения операций - Сверка итогов
     */
    START_RECONCILIATION,
    /**
     * Данная функция предназначена для вызова сервисного меню
     */
    START_SERVICE_MENU,
    /**
     * Данная функция предназначена для добавления тестовой конфигурации в терминале (на этапе тестов)
     */
    ADD_TEST_CONFIGURATION,
    /**
     * Данная функция предназначена для выполнения операций - Выдача наличных
     */
    CASHOUT,
    /**
     * Данная функция предназначена для выполнения операций - Оплата со сдачей
     */
    PURCHASE_WITH_CASHBACK
}