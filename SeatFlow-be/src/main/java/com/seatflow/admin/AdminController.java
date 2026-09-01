package com.seatflow.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

	private final AdminService adminService;
	private final AdminDashboardService adminDashboardService;

	public AdminController(AdminService adminService, AdminDashboardService adminDashboardService) {
		this.adminService = adminService;
		this.adminDashboardService = adminDashboardService;
	}

	@GetMapping
	public AdminStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
		return adminService.getAdminStatus(jwt);
	}

	@GetMapping("/dashboard")
	public AdminDashboardResponse dashboard() {
		return adminDashboardService.getDashboard();
	}
}
