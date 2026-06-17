package com.isbrain.codebaseanalyzer.model;

public record ArchitectureBoundaryFinding(
		String className,
		String type,
		FindingSeverity severity
) {
}
