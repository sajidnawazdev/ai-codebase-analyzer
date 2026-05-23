package com.isbrain.codebaseanalyzer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class SampleController {

	private final UserService userService;
	private final AuditService auditService;

	public SampleController(UserService userService, AuditService auditService) {
		this.userService = userService;
		this.auditService = auditService;
	}

	@GetMapping
	public String getAll() {
		return "all";
	}

	@PostMapping
	public String create() {
		return "created";
	}
}
