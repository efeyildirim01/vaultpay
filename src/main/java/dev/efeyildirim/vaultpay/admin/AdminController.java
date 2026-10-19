package dev.efeyildirim.vaultpay.admin;

import dev.efeyildirim.vaultpay.admin.AdminService.AuditPage;
import dev.efeyildirim.vaultpay.admin.AdminService.Overview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only: SecurityConfig requires the audit:read scope for everything under /api/admin. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return adminService.overview();
    }

    @GetMapping("/audit")
    public AuditPage audit(@RequestParam(required = false) @Size(max = 50) String action,
                           @RequestParam(defaultValue = "0") @Min(0) int page,
                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return adminService.audit(action, page, size);
    }
}
