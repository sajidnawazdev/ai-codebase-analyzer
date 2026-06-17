package com.isbrain.codebaseanalyzer.model;

import java.util.List;

public record ArchitectureStyleAssessment(
		ArchitectureStyle primary,
		FindingConfidence confidence,
		List<String> reasoning
) {
}
