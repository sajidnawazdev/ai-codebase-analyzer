package com.isbrain.codebaseanalyzer.model;

public record FindingImpact(
		String shortTermImpact,
		String longTermImpact,
		RiskLevel growthRisk
) {
	public static FindingImpact none() {
		return new FindingImpact("No meaningful impact.", "No meaningful long-term impact identified.", RiskLevel.LOW);
	}
}
