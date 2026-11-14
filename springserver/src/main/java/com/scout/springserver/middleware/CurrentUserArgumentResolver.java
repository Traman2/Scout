package com.scout.springserver.middleware;

import com.scout.springserver.util.AuthUser;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets a controller method take an `AuthUser user` parameter — the
 * equivalent of reading `req.user!` in an Express handler behind requireAuth.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Object user = webRequest.getAttribute(IdentifyUserFilter.USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (user == null) {
            // Only reachable if a route using AuthUser isn't covered by RequireAuthInterceptor.
            throw new IllegalStateException("AuthUser requested on a route without requireAuth");
        }
        return user;
    }
}
