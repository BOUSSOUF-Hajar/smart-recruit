package com.smartrecruit.shared.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.ParameterizedType;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Injecte un {@link CurrentUser} dans les méthodes de contrôleur qui en déclarent un :
 * {@code public UserResponse me(CurrentUser user)}.
 * <p>
 * Les contrôleurs ne manipulent ainsi jamais le JWT ni le {@code SecurityContext}.
 */
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType()) || isOptionalCurrentUser(parameter);
    }

    /**
     * {@code CurrentUser} sur un endpoint protégé ; {@code Optional<CurrentUser>} sur un endpoint public
     * qui adapte sa réponse si un token est présent (détail d'une offre : « déjà postulé »).
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<CurrentUser> user = authentication instanceof JwtAuthenticationToken token
                ? Optional.of(fromJwt(token.getToken()))
                : Optional.empty();
        if (isOptionalCurrentUser(parameter)) {
            return user;
        }
        // Ne devrait jamais arriver : la configuration de sécurité exige un token sur ces routes.
        return user.orElseThrow(() ->
                new IllegalStateException("CurrentUser demandé sur un endpoint public : " + parameter.getMethod()));
    }

    private static boolean isOptionalCurrentUser(MethodParameter parameter) {
        return Optional.class.equals(parameter.getParameterType())
                && parameter.getGenericParameterType() instanceof ParameterizedType type
                && CurrentUser.class.equals(type.getActualTypeArguments()[0]);
    }

    static CurrentUser fromJwt(Jwt jwt) {
        String companyId = jwt.getClaimAsString(JwtClaims.COMPANY_ID);
        return new CurrentUser(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject(), "sub")),
                Role.valueOf(jwt.getClaimAsString(JwtClaims.ROLE)),
                companyId == null ? null : UUID.fromString(companyId));
    }
}
