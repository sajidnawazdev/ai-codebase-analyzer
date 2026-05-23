package com.isbrain.codebaseanalyzer.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AllHttpMethodsController {

	@GetMapping("/items")
	public String getItems() {
		return "items";
	}

	@PostMapping("/items")
	public String createItem() {
		return "created";
	}

	@PutMapping("/items/{id}")
	public String updateItem() {
		return "updated";
	}

	@DeleteMapping("/items/{id}")
	public String deleteItem() {
		return "deleted";
	}

	@PatchMapping("/items/{id}")
	public String patchItem() {
		return "patched";
	}
}
