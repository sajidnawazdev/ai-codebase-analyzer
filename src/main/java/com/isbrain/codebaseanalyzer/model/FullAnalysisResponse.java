package com.isbrain.codebaseanalyzer.model;

import java.util.List;

public record FullAnalysisResponse(
		AiAnalysisReport aiReport,
		String mermaidDiagram,
		List<String> violations,
		ProjectAnalysisResult rawAnalysis
) {
}
