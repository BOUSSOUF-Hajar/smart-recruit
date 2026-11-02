package com.smartrecruit.identity.web;

import com.smartrecruit.identity.application.AccountService;
import com.smartrecruit.identity.application.AuthResult;
import com.smartrecruit.identity.application.AuthService;
import com.smartrecruit.identity.application.ClientInfo;
import com.smartrecruit.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints d'authentification ({@code docs/api.md} §2). Le contrôleur ne fait que traduire HTTP :
 * lecture du cookie, origine de la requête, pose du nouveau cookie.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;
    private final AccountService accountService;
    private final RefreshCookies cookies;

    AuthController(AuthService authService, AccountService accountService, RefreshCookies cookies) {
        this.authService = authService;
        this.accountService = accountService;
        this.cookies = cookies;
    }

    /** US-06. 201 sans en-tête Location : la ressource créée est la session, pas une URL consultable. */
    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return withSession(HttpStatus.CREATED, authService.register(request.toCommand(), client(http)));
    }

    /** US-07 */
    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withSession(HttpStatus.OK, authService.login(request.email(), request.password(), client(http)));
    }

    /** US-08 */
    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String token,
                                         HttpServletRequest http) {
        return withSession(HttpStatus.OK, authService.refresh(token, client(http)));
    }

    /** US-09. Efface toujours le cookie, même si la session était déjà fermée. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    /** US-11 */
    @PostMapping("/password")
    ResponseEntity<Void> changePassword(CurrentUser user, @Valid @RequestBody ChangePasswordRequest request,
                                        @CookieValue(name = RefreshCookies.NAME, required = false) String token) {
        accountService.changePassword(user, request.currentPassword(), request.newPassword(), token);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(result.refreshToken()))
                .cacheControl(CacheControl.noStore())
                .body(AuthResponse.of(result));
    }

    private static ClientInfo client(HttpServletRequest http) {
        return new ClientInfo(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }
}
