package com.isbrain.codebaseanalyzer.model;

public record ArchitectureObservation(
		String type,
		String className,
		String description,
		int confidence,
		boolean heuristic,
		FindingConfidence findingConfidence,
		FindingSeverity severity
) {
	public ArchitectureObservation(String type, String className, String description, int confidence, boolean heuristic) {
		this(type, className, description, confidence, heuristic,
				heuristic ? FindingConfidence.POSSIBLE : FindingConfidence.LIKELY,
				FindingSeverity.MEDIUM);
	}
}
