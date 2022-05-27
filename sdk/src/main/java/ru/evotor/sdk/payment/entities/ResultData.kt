package ru.evotor.sdk.payment.entities

data class ResultData(
    /**
     * merchant id
     */
    val MID: String,
    /**
     * Замаскированный номер карты
     */
    val PAN: String,
    /**
     * Подсчитанный хеш карты
     */
    val HASH: String,
    /**
     * Уникальный номер операции
     */
    val REQUEST_ID: String,
    /**
     * Номер операции в батче терминала
     */
    val TSN: String,
    /**
     * Время проведения операции
     */
    val TIME: String,
    /**
     * Номер транзакции
     */
    val RRN: String,
    /**
     * Алгоритм шифрования
     */
    val HASH_ALGO: String,
    /**
     * Признак своя/чужая карта
     */
    val IS_OWN: String,
    /**
     * Тип платежной системы
     */
    val CARD_NAME: String,
    /**
     * Дата проведения операции
     */
    val DATE: String,
    /**
     * terminal id
     */
    val TID: String,
    /**
     * Сумма транзакции
     */
    val AMOUNT: String,
    /**
     * Чистая сумма транзакции без дополнительных тэгов (Спасибо, чаевые, комиссии и т.п.)
     */
    val AMOUNT_C: String,
    /**
     * Зашифрованные данные карты
     */
    val ENCRYPTED_DATA: String,
    /**
     * Владелец карты
     */
    val HOLDENAME: String,
    /**
     * Флаги операции
     */
    val FLAGS: String,
    /**
     * Срок действия карты
     */
    val EXP_DATE: String,
    /**
     * Номер программы лояльности (функционал для X5)
     */
    val LLT_ID: String,
    /**
     * Код авторизации
     */
    val AUTH_CODE: String,
    /**
     * Статус операции
     */
    val MESSAGE: String,
    /**
     * Тип транзакции (1 - оплата, 3 - возврат, 7 - сверка итогов, 8 - отмена, 11 - сервисное меню,
     * 19 - загрузка тестовой конфигурации, 61 - оплата со сдачей, 62 - выдача наличных)
     * - параметр может отсутствовать для операций: вызов сервисного меню, загрузка тестовой конфигурации,
     * и сверка
     */
    val PIL_OP_TYPE: String,
    /**
     * Результат выполнения функции
     */
    val ERROR: String,
    /**
     * ???
     */
    val CARD_ID: String
)
