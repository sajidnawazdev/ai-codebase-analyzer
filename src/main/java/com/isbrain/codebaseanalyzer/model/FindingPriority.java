package com.isbrain.codebaseanalyzer.model;

public record FindingPriority(int score) {
	public FindingPriority {
		if (score < 0 || score > 100) {
			throw new IllegalArgumentException("Finding priority score must be between 0 and 100");
		}
	}
}
