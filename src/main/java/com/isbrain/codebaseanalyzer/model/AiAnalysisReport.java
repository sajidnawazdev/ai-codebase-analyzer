package com.isbrain.codebaseanalyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysisReport(
		String overallAssessment,
		int architectureScore,
		List<String> strengths,
		List<String> keyConcerns,
		List<String> improvements,
		Map<String, Integer> categoryScores,
		List<String> observationAnalysis,
		List<EvidenceBasedFinding> findings
) {
	@JsonProperty("criticalIssues")
	public List<String> criticalIssues() {
		return keyConcerns;
	}
}
