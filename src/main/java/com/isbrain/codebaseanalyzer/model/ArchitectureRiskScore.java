package com.isbrain.codebaseanalyzer.model;

public record ArchitectureRiskScore(
		int layeringRisk,
		int couplingRisk,
		int modularityRisk,
		int maintainabilityRisk,
		int scalabilityRisk
) {
}
