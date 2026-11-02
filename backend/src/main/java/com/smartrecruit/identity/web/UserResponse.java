package com.smartrecruit.identity.web;

import com.smartrecruit.identity.domain.User;
import com.smartrecruit.shared.security.Role;

import java.util.UUID;

/** L'utilisateur tel que le voit le frontend ; jamais de hash, ni de date de consentement. */
public record UserResponse(UUID id, String email, String firstName, String lastName, Role role,
                           UUID companyId, boolean mustChangePassword) {

    static UserResponse of(User user, UUID companyId) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole(), companyId, user.isMustChangePassword());
    }
}
