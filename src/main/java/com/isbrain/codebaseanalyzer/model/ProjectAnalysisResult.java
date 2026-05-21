package com.isbrain.codebaseanalyzer.model;

import java.util.List;

public record ProjectAnalysisResult(
		AnalysisSummary summary,
		List<String> relationships,
		List<EndpointAnalysis> endpoints,
		List<PackageAnalysis> packages,
		List<ClassAnalysis> classes,
		List<String> violations,
		List<String> circularDependencies,
		List<String> godClasses,
		List<String> emptyControllers,
		List<String> orphanServices,
		List<String> fatControllers,
		List<String> couplingRanking,
		String mermaidDiagram
) {
}
