package com.isbrain.codebaseanalyzer.model;

import jakarta.persistence.Entity;

@Entity
public class SampleEntity {

	private Long id;
	private String name;

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
