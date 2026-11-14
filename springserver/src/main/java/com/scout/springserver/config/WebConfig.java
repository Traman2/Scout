package com.scout.springserver.config;

import com.scout.springserver.middleware.CurrentUserArgumentResolver;
import com.scout.springserver.middleware.RequireAuthInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Equivalent of the middleware/route mounting in backend/src/app.ts:
 * CORS with credentials for CORS_ORIGIN, and requireAuth on the same routes
 * the Express routers protect.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;
    private final RequireAuthInterceptor requireAuthInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(
            AppProperties props,
            RequireAuthInterceptor requireAuthInterceptor,
            CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.props = props;
        this.requireAuthInterceptor = requireAuthInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(props.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // router.use(requireAuth) in space/university routes, and requireAuth on GET /api/auth/me
        registry.addInterceptor(requireAuthInterceptor)
                .addPathPatterns(
                        "/api/spaces", "/api/spaces/**",
                        "/api/university", "/api/university/**",
                        "/api/auth/me");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
