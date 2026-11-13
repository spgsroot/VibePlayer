package ru.spgsroot.peachy.translate

/** Why a page could not be translated; each case has its own message in the reader. */
sealed interface TranslationError {
    /** No key, no model, or a base URL the app refuses to use. */
    object NotConfigured : TranslationError

    /** The provider rejected the key (HTTP 401 or 403). */
    object Auth : TranslationError

    /** HTTP 429. */
    object RateLimit : TranslationError

    /** No connection, a timeout, or a broken transfer. */
    object Network : TranslationError

    /** The provider answered, but not with a usable translation, even after one more try. */
    object BadResponse : TranslationError

    /** The model declined to read the page: no message in the reply, or a content filter. */
    object Refused : TranslationError

    /** Any other HTTP failure. */
    data class Server(val code: Int) : TranslationError
}

/** The only failure type translation code throws, besides coroutine cancellation. */
class TranslationException(val error: TranslationError, cause: Throwable? = null) :
    Exception(error.toString(), cause)
