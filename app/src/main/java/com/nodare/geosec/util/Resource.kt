package com.nodare.geosec.util

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(
        val message: String,
        val exception: Throwable? = null,
        val errorType: Any? = null
    ) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()
}
