package com.isbrain.codebaseanalyzer.model;

import java.util.List;
import java.util.Map;

public record AiAnalysisReport(
		String overallAssessment,
		int architectureScore,
		List<String> strengths,
		List<String> criticalIssues,
		List<String> improvements,
		Map<String, Integer> categoryScores,
		List<String> layerViolationAnalysis,
		List<String> circularDependencyAnalysis,
		List<String> godClassAnalysis,
		List<String> orphanServiceAnalysis,
		List<String> fatControllerAnalysis,
		List<String> emptyControllerAnalysis
) {
}
