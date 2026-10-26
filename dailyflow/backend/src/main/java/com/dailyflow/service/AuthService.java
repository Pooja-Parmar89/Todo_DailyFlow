package com.dailyflow.service;

import com.dailyflow.domain.ActivityAction;
import com.dailyflow.domain.User;
import com.dailyflow.dto.AuthDtos.AuthResponse;
import com.dailyflow.dto.AuthDtos.ChangePasswordRequest;
import com.dailyflow.dto.AuthDtos.LoginRequest;
import com.dailyflow.dto.AuthDtos.RegisterRequest;
import com.dailyflow.dto.AuthDtos.UpdateProfileRequest;
import com.dailyflow.dto.AuthDtos.UserDto;
import com.dailyflow.exception.ConflictException;
import com.dailyflow.exception.FieldValidationException;
import com.dailyflow.exception.NotFoundException;
import com.dailyflow.mapper.TaskMapper;
import com.dailyflow.repository.UserRepository;
import com.dailyflow.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CategoryService categoryService;
    private final ActivityService activityService;
    private final TaskMapper mapper;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       CategoryService categoryService, ActivityService activityService, TaskMapper mapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.categoryService = categoryService;
        this.activityService = activityService;
        this.mapper = mapper;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()),
                request.displayName().trim()));
        categoryService.createDefaults(user.getId());
        activityService.record(user.getId(), null, null, ActivityAction.REGISTER, "Account created");
        return authResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        // Throws BadCredentialsException (-> 401 with a generic message) for unknown email or wrong password.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = userRepository.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
        activityService.record(user.getId(), null, null, ActivityAction.LOGIN, "Signed in");
        return authResponse(user);
    }

    /** Invalidates every token issued so far for this user (token_version bump). */
    @Transactional
    public void logout(Long userId) {
        User user = load(userId);
        user.incrementTokenVersion();
        activityService.record(userId, null, null, ActivityAction.LOGOUT, "Signed out");
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return mapper.toDto(load(userId));
    }

    @Transactional
    public UserDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = load(userId);
        user.setDisplayName(request.displayName().trim());
        user.setEmailRemindersEnabled(request.emailRemindersEnabled());
        user.setReminderLeadMinutes(request.reminderLeadMinutes());
        return mapper.toDto(user);
    }

    /** Changes the password, invalidates old tokens and returns a fresh token so this session continues. */
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new FieldValidationException("currentPassword", "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.incrementTokenVersion();
        activityService.record(userId, null, null, ActivityAction.PASSWORD_CHANGED, "Password changed");
        return authResponse(user);
    }

    private User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpirationSeconds(),
                mapper.toDto(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
