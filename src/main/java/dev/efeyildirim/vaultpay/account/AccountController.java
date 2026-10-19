package dev.efeyildirim.vaultpay.account;

import dev.efeyildirim.vaultpay.account.AccountService.AccountView;
import dev.efeyildirim.vaultpay.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    public record OpenAccountRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenAccountRequest request) {
        return accountService.open(CurrentUser.id(jwt), request.name(), request.currency());
    }

    @GetMapping
    public List<AccountView> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.listFor(CurrentUser.id(jwt));
    }

    @GetMapping("/{accountId}")
    public AccountView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        return accountService.get(CurrentUser.id(jwt), accountId);
    }
}
