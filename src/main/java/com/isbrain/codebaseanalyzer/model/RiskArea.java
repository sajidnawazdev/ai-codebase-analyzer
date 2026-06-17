package com.isbrain.codebaseanalyzer.model;

import java.util.List;

public record RiskArea(
		String name,
		RiskLevel level,
		List<MergedFinding> findings,
		String summary
) {
}
