package com.isbrain.codebaseanalyzer.model;

import java.util.List;

public record ClassAnalysis(
		String className,
		String packageName,
		List<String> annotations,
		List<String> methods,
		List<String> dependencies,
		List<String> allDependencies,
		ComponentType componentType,
		String superClass,
		List<String> implementedInterfaces,
		int couplingScore
) {
}
