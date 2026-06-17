package com.isbrain.codebaseanalyzer.model;

public record ClassMetrics(
		String className,
		String packageName,
		ComponentType componentType,
		int methodCount,
		int publicMethodCount,
		int fieldCount,
		int dependencyCount,
		int constructorParameterCount,
		int lineCount
) {
}
