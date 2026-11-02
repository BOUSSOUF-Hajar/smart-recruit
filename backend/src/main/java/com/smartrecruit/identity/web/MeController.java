package com.smartrecruit.identity.web;

import com.smartrecruit.identity.application.AccountService;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /me} : accessible même tant que le mot de passe temporaire n'a pas été changé. */
@RestController
class MeController {

    private final AccountService accountService;

    MeController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/api/v1/me")
    UserResponse me(CurrentUser user) {
        AccountService.AccountView view = accountService.me(user);
        return UserResponse.of(view.user(), view.companyId());
    }
}
