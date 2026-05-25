package com.isbrain.codebaseanalyzer.model;

import java.time.LocalDateTime;
import java.util.Map;

public record AnalysisCompletedEvent(
		String projectName,
		String projectPath,
		int architectureScore,
		Map<String, Integer> categoryScores,
		int violationCount,
		LocalDateTime analysedAt
) {
}
