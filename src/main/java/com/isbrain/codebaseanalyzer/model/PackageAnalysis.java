package com.isbrain.codebaseanalyzer.model;

import java.util.Map;

public record PackageAnalysis(
		String packageName,
		int classCount,
		Map<ComponentType, Long> componentTypeDistribution
) {
}
