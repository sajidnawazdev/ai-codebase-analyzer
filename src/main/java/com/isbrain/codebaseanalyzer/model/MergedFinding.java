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
		String recommendation,
		FindingImpact findingImpact
) {
	public MergedFinding(
			String title,
			FindingSeverity severity,
			FindingConfidence confidence,
			int priority,
			Set<String> affectedClasses,
			List<String> evidence,
			String impact,
			String recommendation
	) {
		this(title, severity, confidence, priority, affectedClasses, evidence, impact, recommendation,
				FindingImpact.none());
	}

	public MergedFinding {
		if (findingImpact == null) {
			findingImpact = FindingImpact.none();
		}
	}
}
