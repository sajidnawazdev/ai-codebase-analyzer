package com.isbrain.codebaseanalyzer.model;

import java.util.List;
import java.util.Map;

public record ArchitectureScoreGuidance(
		Map<String, Integer> minimumCategoryScores,
		List<String> rulesApplied,
		String assessmentGuidance
) {
}
