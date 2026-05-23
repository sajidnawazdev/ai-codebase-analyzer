package com.isbrain.codebaseanalyzer.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/empty")
public class NoEndpointMethods {

	public String helperMethod() {
		return "not an endpoint";
	}

	private void internalMethod() {
	}
}
