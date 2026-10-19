package dev.efeyildirim.vaultpay.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Creates the two simulator staff accounts at startup, so the review queue and the admin panel can be
 * tried without database access: {@value #REVIEWER_EMAIL} and {@value #ADMIN_EMAIL}.
 * Only active when {@code vaultpay.staff.password} is set (always in dev; opt-in elsewhere via
 * VAULTPAY_STAFF_PASSWORD). Existing accounts are left untouched, so restarts are harmless.
 */
@Component
@ConditionalOnExpression("!'${vaultpay.staff.password:}'.isEmpty()")
class StaffBootstrap implements ApplicationRunner {

    static final String REVIEWER_EMAIL = "reviewer@vaultpay.local";
    static final String ADMIN_EMAIL = "admin@vaultpay.local";

    private final UserService userService;
    private final UserRepository users;
    private final String password;

    StaffBootstrap(UserService userService, UserRepository users, @Value("${vaultpay.staff.password}") String password) {
        this.userService = userService;
        this.users = users;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(REVIEWER_EMAIL, "Simulator Reviewer", UserRole.REVIEWER);
        createIfMissing(ADMIN_EMAIL, "Simulator Admin", UserRole.ADMIN);
    }

    private void createIfMissing(String email, String fullName, UserRole role) {
        if (users.existsByEmail(email)) {
            return;
        }
        User user = userService.register(email, password, fullName);
        userService.grantRole(null, user.getId(), role);
    }
}
