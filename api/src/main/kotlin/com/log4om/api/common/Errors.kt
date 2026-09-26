package com.log4om.api.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiError(val error: String, val details: List<String> = emptyList())

class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : RuntimeException(message)
class UnauthorizedException(message: String) : RuntimeException(message)

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException::class)
    fun notFound(ex: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError(ex.message ?: "Not found"))

    @ExceptionHandler(ConflictException::class)
    fun conflict(ex: ConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError(ex.message ?: "Conflict"))

    @ExceptionHandler(BadRequestException::class)
    fun badRequest(ex: BadRequestException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError(ex.message ?: "Bad request"))

    @ExceptionHandler(UnauthorizedException::class)
    fun unauthorized(ex: UnauthorizedException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError(ex.message ?: "Unauthorized"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val details = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ApiError("Validation failed", details))
    }

    @ExceptionHandler(Exception::class)
    fun generic(ex: Exception) =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError(ex.message ?: "Internal error"))
}
