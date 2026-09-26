package com.log4om.api.auth

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterRequest) = authService.register(req)

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest) = authService.login(req)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody req: RefreshRequest) = authService.refresh(req)

    @PostMapping("/logout")
    fun logout(@Valid @RequestBody req: RefreshRequest) {
        authService.logout(req.refreshToken)
    }
}
