package com.isbrain.codebaseanalyzer.model;

import java.util.List;
import java.util.Set;

public record MergedFinding(
		String title,
		FindingSeverity severity,
		FindingConfidence confidence,
		int priority,
		Set<String> affectedClasses,
		List<String> evidence,
		String impact,
		String recommendation
) {
}
