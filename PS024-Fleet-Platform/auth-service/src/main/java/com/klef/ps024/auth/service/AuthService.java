package com.klef.ps024.auth.service;

import com.klef.ps024.auth.dto.AuthResponse;
import com.klef.ps024.auth.dto.LoginRequest;
import com.klef.ps024.auth.dto.RegisterRequest;
import com.klef.ps024.auth.entity.User;
import com.klef.ps024.auth.entity.UserRole;
import com.klef.ps024.auth.exception.DuplicateEmailException;
import com.klef.ps024.auth.exception.InvalidCredentialsException;
import com.klef.ps024.auth.exception.PasswordTooLongException;
import com.klef.ps024.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        validatePasswordLength(request.password());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        User user = new User(email, passwordEncoder.encode(request.password()), UserRole.STAFF);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new AuthResponse(jwtService.generateAccessToken(user), "Bearer");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new PasswordTooLongException();
        }
    }
}
