package dev.efeyildirim.vaultpay.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class RateLimitWebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor interceptor;

    RateLimitWebConfig(RateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/auth/login", "/api/transfers", "/api/accounts/*/deposits");
    }
}
