package dev.efeyildirim.vaultpay.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local development only: creates a reviewer account at startup so the review queue can be tried
 * without database access (the dev database is in-memory and starts empty). Never active in prod.
 */
@Component
@Profile("dev")
class DevReviewerBootstrap implements ApplicationRunner {

    private final UserService userService;
    private final UserRepository users;
    private final String email;
    private final String password;

    DevReviewerBootstrap(UserService userService, UserRepository users,
                         @Value("${vaultpay.dev.reviewer-email}") String email,
                         @Value("${vaultpay.dev.reviewer-password}") String password) {
        this.userService = userService;
        this.users = users;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(UserService.normaliseEmail(email))) {
            return;
        }
        User reviewer = userService.register(email, password, "Dev Reviewer");
        userService.promoteToReviewer(null, reviewer.getId());
    }
}
