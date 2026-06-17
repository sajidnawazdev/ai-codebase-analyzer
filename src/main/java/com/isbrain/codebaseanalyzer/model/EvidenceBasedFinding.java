package com.isbrain.codebaseanalyzer.model;

import java.util.List;

public record EvidenceBasedFinding(
		String title,
		FindingSeverity severity,
		FindingConfidence confidence,
		List<String> affectedClasses,
		List<String> evidence,
		String impact,
		String recommendation,
		FindingCategory category,
		FindingPriority priority
) {
	public EvidenceBasedFinding(
			String title,
			FindingSeverity severity,
			FindingConfidence confidence,
			List<String> affectedClasses,
			List<String> evidence,
			String impact,
			String recommendation
	) {
		this(title, severity, confidence, affectedClasses, evidence, impact, recommendation,
				FindingCategory.DESIGN, priorityFor(severity, confidence));
	}

	public EvidenceBasedFinding {
		if (category == null) {
			category = FindingCategory.DESIGN;
		}
		if (priority == null) {
			priority = priorityFor(severity, confidence);
		}
	}

	public static FindingPriority priorityFor(FindingSeverity severity, FindingConfidence confidence) {
		return new FindingPriority((int) (severityWeight(severity) * confidenceMultiplier(confidence)));
	}

	private static int severityWeight(FindingSeverity severity) {
		return switch (severity == null ? FindingSeverity.INFO : severity) {
			case CRITICAL -> 100;
			case HIGH -> 75;
			case MEDIUM -> 50;
			case LOW -> 25;
			case INFO -> 10;
		};
	}

	private static double confidenceMultiplier(FindingConfidence confidence) {
		return switch (confidence == null ? FindingConfidence.POSSIBLE : confidence) {
			case CONFIRMED -> 1.0;
			case LIKELY -> 0.8;
			case POSSIBLE -> 0.5;
		};
	}
}
