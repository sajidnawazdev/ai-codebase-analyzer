package com.isbrain.codebaseanalyzer.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/legacy")
public class RequestMappingController {

	@RequestMapping(value = "/data", method = RequestMethod.GET)
	public String getData() {
		return "data";
	}

	@RequestMapping(path = "/info", method = RequestMethod.POST)
	public String postInfo() {
		return "info";
	}
}
