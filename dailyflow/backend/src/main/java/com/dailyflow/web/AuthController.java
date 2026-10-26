package com.dailyflow.web;

import com.dailyflow.dto.AuthDtos.AuthResponse;
import com.dailyflow.dto.AuthDtos.ChangePasswordRequest;
import com.dailyflow.dto.AuthDtos.LoginRequest;
import com.dailyflow.dto.AuthDtos.RegisterRequest;
import com.dailyflow.dto.AuthDtos.UpdateProfileRequest;
import com.dailyflow.dto.AuthDtos.UserDto;
import com.dailyflow.security.AppUserDetails;
import com.dailyflow.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Authentication and profile")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Invalidates all tokens issued to this user. The client must also discard its stored token. */
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AppUserDetails me) {
        authService.logout(me.getId());
    }

    @GetMapping("/auth/me")
    public UserDto me(@AuthenticationPrincipal AppUserDetails me) {
        return authService.me(me.getId());
    }

    @PutMapping("/users/me")
    public UserDto updateProfile(@AuthenticationPrincipal AppUserDetails me,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(me.getId(), request);
    }

    @PutMapping("/users/me/password")
    public AuthResponse changePassword(@AuthenticationPrincipal AppUserDetails me,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(me.getId(), request);
    }
}
