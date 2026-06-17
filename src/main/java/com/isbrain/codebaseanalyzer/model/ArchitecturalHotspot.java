package com.isbrain.codebaseanalyzer.model;

public record ArchitecturalHotspot(
		String className,
		String type,
		int riskScore,
		String explanation
) {
}
