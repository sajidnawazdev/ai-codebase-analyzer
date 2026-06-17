package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitecturalHotspot;
import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureScoreAlignmentServiceTest {

	private ArchitectureScoreAlignmentService service;

	@BeforeEach
	void setUp() {
		service = new ArchitectureScoreAlignmentService();
	}

	@Test
	void setsDependencyMinimumWhenNoCyclesNoSignificantHotspotsAndLowCoupling() {
		var guidance = service.align(List.of(), List.of(), List.of(classOf("OwnerController", 3)));

		assertEquals(7, guidance.minimumCategoryScores().get("dependencies"));
	}

	@Test
	void doesNotSetDependencyMinimumWhenSignificantHotspotExists() {
		var guidance = service.align(
				List.of(),
				List.of(new ArchitecturalHotspot("MegaService", "HIGH_COUPLING", 8, "High coupling.")),
				List.of(classOf("MegaService", 3))
		);

		assertNotEquals(Integer.valueOf(7), guidance.minimumCategoryScores().get("dependencies"));
	}

	@Test
	void setsLayeringMinimumForLowRiskControllerRepositoryAccess() {
		var guidance = service.align(
				List.of(observation("LAYERING", FindingConfidence.POSSIBLE, FindingSeverity.LOW)),
				List.of(),
				List.of(classOf("OwnerController", 2))
		);

		assertEquals(7, guidance.minimumCategoryScores().get("layering"));
	}

	@Test
	void setsAllCategoryMinimumsWhenAllConcernsArePossibleOrLowSeverity() {
		var guidance = service.align(
				List.of(observation("LAYERING", FindingConfidence.POSSIBLE, FindingSeverity.LOW)),
				List.of(),
				List.of(classOf("OwnerController", 2))
		);

		assertEquals(6, guidance.minimumCategoryScores().get("modularity"));
		assertEquals(6, guidance.minimumCategoryScores().get("maintainability"));
		assertEquals(6, guidance.minimumCategoryScores().get("scalability"));
	}

	@Test
	void includesSmallCrudAssessmentGuidance() {
		var guidance = service.align(List.of(), List.of(), List.of());

		assertTrue(guidance.assessmentGuidance().contains("simple, reasonable structure"));
	}

	private ArchitectureObservation observation(String type, FindingConfidence confidence, FindingSeverity severity) {
		return new ArchitectureObservation(type, "OwnerController", "OwnerController accesses OwnerRepository directly.",
				60, true, confidence, severity);
	}

	private ClassAnalysis classOf(String className, int couplingScore) {
		return new ClassAnalysis(className, "com.app", List.of(), List.of(), List.of(), List.of(),
				ComponentType.REST_CONTROLLER, null, List.of(), couplingScore);
	}
}
