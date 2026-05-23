package com.isbrain.codebaseanalyzer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoBasePath {

	@GetMapping("/health")
	public String health() {
		return "ok";
	}
}
