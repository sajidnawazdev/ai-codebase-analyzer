package com.isbrain.codebaseanalyzer.model;

public record AnalysisSummary(
		long controllers,
		long services,
		long repositories,
		long components,
		long configurations,
		long entities,
		long controllerAdvices,
		long restControllerAdvices
) {
}
