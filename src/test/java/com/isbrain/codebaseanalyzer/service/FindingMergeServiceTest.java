package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.EvidenceBasedFinding;
import com.isbrain.codebaseanalyzer.model.ArchitectureMaturity;
import com.isbrain.codebaseanalyzer.model.ArchitectureStyle;
import com.isbrain.codebaseanalyzer.model.FindingCategory;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingPriority;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import com.isbrain.codebaseanalyzer.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FindingMergeServiceTest {

	private final FindingMergeService mergeService = new FindingMergeService();

	@Test
	void groupsControllerRepositoryAccessIntoOneRootCauseFinding() {
		var finding = new EvidenceBasedFinding(
				"Controller-to-repository access",
				FindingSeverity.LOW,
				FindingConfidence.POSSIBLE,
				List.of("PetController", "OwnerController", "VisitController", "VetController"),
				List.of(
						"PetController -> OwnerRepository",
						"OwnerController -> OwnerRepository",
						"VisitController -> OwnerRepository",
						"VetController -> VetRepository"
				),
				"Acceptable for this small CRUD application.",
				"Introduce application services only when business logic grows.",
				FindingCategory.ARCHITECTURE,
				new FindingPriority(12)
		);

		var merged = mergeService.merge(List.of(finding));

		assertEquals(1, merged.size());
		var result = merged.get(0);
		assertEquals("Controller-to-Repository Access Pattern", result.title());
		assertEquals(FindingSeverity.LOW, result.severity());
		assertEquals(FindingConfidence.POSSIBLE, result.confidence());
		assertEquals(18, result.priority());
		assertEquals(Set.of("PetController", "OwnerController", "VisitController", "VetController"), result.affectedClasses());
		assertEquals(4, result.evidence().size());
	}

	@Test
	void mergedControllerRepositoryFindingUsesEarlyStageSimpleCrudConsequence() {
		var finding = new EvidenceBasedFinding(
				"Controller-to-repository access",
				FindingSeverity.LOW,
				FindingConfidence.POSSIBLE,
				List.of("OwnerController"),
				List.of("OwnerController -> OwnerRepository"),
				"Acceptable for this small CRUD application.",
				"Introduce application services only when business logic grows.",
				FindingCategory.ARCHITECTURE,
				new FindingPriority(12)
		);

		var result = mergeService.merge(
				List.of(finding),
				ArchitectureMaturity.EARLY_STAGE,
				ArchitectureStyle.SIMPLE_CRUD
		).get(0);

		assertEquals("No meaningful impact.", result.findingImpact().shortTermImpact());
		assertEquals(RiskLevel.LOW, result.findingImpact().growthRisk());
		assertEquals("Current design is appropriate for an early-stage CRUD application. No meaningful short-term impact.",
				result.impact());
		assertEquals("Avoid premature abstraction. Keep the current design until business rules become reusable, transactional, or shared.",
				result.recommendation());
	}

	@Test
	void mergesDuplicateRootCauseFindingsByFingerprint() {
		var directRepository = new EvidenceBasedFinding(
				"Controller-to-repository access",
				FindingSeverity.LOW,
				FindingConfidence.POSSIBLE,
				List.of("OwnerController"),
				List.of("OwnerController -> OwnerRepository"),
				"Impact one.",
				"Recommendation one.",
				FindingCategory.ARCHITECTURE,
				new FindingPriority(12)
		);
		var missingService = new EvidenceBasedFinding(
				"Missing service abstraction",
				FindingSeverity.LOW,
				FindingConfidence.POSSIBLE,
				List.of("OwnerController"),
				List.of("OwnerController -> OwnerRepository"),
				"Impact two.",
				"Recommendation two.",
				FindingCategory.ARCHITECTURE,
				new FindingPriority(12)
		);

		var merged = mergeService.merge(List.of(directRepository, missingService));

		assertEquals(1, merged.size());
		assertEquals("Controller-to-Repository Access Pattern", merged.get(0).title());
		assertEquals(Set.of("OwnerController"), merged.get(0).affectedClasses());
		assertEquals(List.of("OwnerController -> OwnerRepository"), merged.get(0).evidence());
	}

	@Test
	void preservesDifferentRootCauseFindings() {
		var controllerRepository = new EvidenceBasedFinding(
				"Controller-to-repository access",
				FindingSeverity.LOW,
				FindingConfidence.POSSIBLE,
				List.of("OwnerController"),
				List.of("OwnerController -> OwnerRepository"),
				"Impact.",
				"Recommendation.",
				FindingCategory.ARCHITECTURE,
				new FindingPriority(12)
		);
		var ownerComplexity = new EvidenceBasedFinding(
				"Domain entity complexity",
				FindingSeverity.LOW,
				FindingConfidence.POSSIBLE,
				List.of("Owner"),
				List.of("Owner has 13 methods"),
				"Impact.",
				"Recommendation.",
				FindingCategory.MAINTAINABILITY,
				new FindingPriority(12)
		);

		var merged = mergeService.merge(List.of(controllerRepository, ownerComplexity));

		assertEquals(2, merged.size());
	}
}
