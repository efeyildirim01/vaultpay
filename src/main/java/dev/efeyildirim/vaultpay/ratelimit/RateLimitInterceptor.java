package dev.efeyildirim.vaultpay.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Two limits, both on POST only:
 * <ul>
 *   <li><b>login</b>, per client IP: slows down password guessing. Keyed by IP because the caller is not
 *       authenticated yet.</li>
 *   <li><b>money</b> (deposits and transfers), per user: runs after Spring Security, so the user id comes
 *       from the verified JWT and cannot be spoofed by changing a header.</li>
 * </ul>
 * Throwing from here goes through the normal exception handlers, so a 429 looks like every other error.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final Duration window;
    private final int loginLimit;
    private final int moneyLimit;

    public RateLimitInterceptor(RateLimiter limiter,
                                @Value("${vaultpay.rate-limit.window}") Duration window,
                                @Value("${vaultpay.rate-limit.login-per-window}") int loginLimit,
                                @Value("${vaultpay.rate-limit.money-per-window}") int moneyLimit) {
        this.limiter = limiter;
        this.window = window;
        this.loginLimit = loginLimit;
        this.moneyLimit = moneyLimit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String key;
        int limit;
        if (request.getRequestURI().equals("/api/auth/login")) {
            // getRemoteAddr(), not X-Forwarded-For: that header is client-controlled unless a trusted proxy sets it.
            key = "login:" + request.getRemoteAddr();
            limit = loginLimit;
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return true; // Spring Security has already rejected anonymous calls to these paths
            }
            key = "money:" + auth.getName();
            limit = moneyLimit;
        }
        RateLimiter.Decision decision = limiter.tryAcquire(key, limit, window);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfter());
        }
        return true;
    }
}
