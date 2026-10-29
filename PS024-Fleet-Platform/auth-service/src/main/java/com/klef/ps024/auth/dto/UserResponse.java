package com.klef.ps024.auth.dto;

import com.klef.ps024.auth.entity.User;
import com.klef.ps024.auth.entity.UserRole;

public record UserResponse(Long id, String email, UserRole role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
