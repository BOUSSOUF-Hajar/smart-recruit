package com.smartrecruit.identity.application;

import com.smartrecruit.identity.api.AccountSummary;
import com.smartrecruit.identity.api.UserAccounts;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.identity.infrastructure.UserRepository;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class UserAccountsService implements UserAccounts {

    private final UserRepository users;

    UserAccountsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountSummary get(UUID userId) {
        return summary(load(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AccountSummary> findAll(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, UserAccountsService::summary));
    }

    @Override
    @Transactional
    public AccountSummary rename(UUID userId, String firstName, String lastName) {
        User user = load(userId);
        user.rename(firstName, lastName);
        return summary(user);
    }

    private User load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Compte"));
    }

    private static AccountSummary summary(User user) {
        return new AccountSummary(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
    }
}
