package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureRiskScore;
import com.isbrain.codebaseanalyzer.model.ArchitectureStyle;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import com.isbrain.codebaseanalyzer.model.MergedFinding;
import com.isbrain.codebaseanalyzer.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAreaAggregatorTest {

	private final RiskAreaAggregator aggregator = new RiskAreaAggregator();

	@Test
	void createsExpectedRiskAreas() {
		var areas = aggregator.aggregate(List.of(), new ArchitectureRiskScore(10, 15, 20, 25, 30),
				ArchitectureStyle.SIMPLE_CRUD);

		assertEquals(7, areas.size());
		assertTrue(areas.stream().anyMatch(area -> area.name().equals("Layering")));
		assertTrue(areas.stream().anyMatch(area -> area.name().equals("Spring Practices")));
	}

	@Test
	void aggregatesControllerRepositoryPatternIntoLayeringRisk() {
		var finding = new MergedFinding(
				"Controller-to-Repository Access Pattern",
				FindingSeverity.LOW,
				FindingConfidence.POSSIBLE,
				18,
				Set.of("OwnerController"),
				List.of("OwnerController -> OwnerRepository"),
				"Current design is acceptable for a small CRUD application.",
				"Introduce application services only if transactional logic emerges."
		);

		var areas = aggregator.aggregate(List.of(finding), new ArchitectureRiskScore(10, 0, 0, 0, 0),
				ArchitectureStyle.SIMPLE_CRUD);

		var layering = areas.stream()
				.filter(area -> area.name().equals("Layering"))
				.findFirst()
				.orElseThrow();

		assertEquals(RiskLevel.LOW, layering.level());
		assertEquals(List.of(finding), layering.findings());
		assertTrue(layering.summary().contains("simple CRUD architecture"));
	}
}
