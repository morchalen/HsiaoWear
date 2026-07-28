package com.example.hsiaowear.data

sealed class Result<out T> {// 通用结果类，用于表示异步操作的结果
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    fun isLoading(): Boolean = this is Loading

    fun getOrNull(): T? {
        return if (this is Success) this.data else null
    }

    fun getExceptionOrNull(): Exception? {
        return if (this is Error) this.exception else null
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(exception: Exception, message: String? = null): Result<Nothing> = Error(exception, message)
        fun error(message: String): Result<Nothing> = Error(Exception(message), message)
        fun loading(): Result<Nothing> = Loading
    }
}