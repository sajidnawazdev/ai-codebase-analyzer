package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ArchitectureMaturity;
import com.isbrain.codebaseanalyzer.model.ArchitectureStyle;
import com.isbrain.codebaseanalyzer.model.FindingCategory;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import com.isbrain.codebaseanalyzer.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceFindingBuilderTest {

	private final EvidenceFindingBuilder builder = new EvidenceFindingBuilder();

	@Test
	void createsDomainEntityComplexityFindingFromOwnerObservation() {
		var observation = new ArchitectureObservation(
				"DOMAIN_ENTITY_COMPLEXITY",
				"Owner",
				"Owner has 13 methods. This is a low-severity possible risk, not a confirmed god class, but should be monitored as the application evolves.",
				70,
				true,
				FindingConfidence.POSSIBLE,
				FindingSeverity.LOW
		);

		var findings = builder.build(List.of(observation));

		assertEquals(1, findings.size());
		var finding = findings.get(0);
		assertEquals("Domain entity complexity", finding.title());
		assertEquals(FindingSeverity.LOW, finding.severity());
		assertEquals(FindingConfidence.POSSIBLE, finding.confidence());
		assertEquals(FindingCategory.MAINTAINABILITY, finding.category());
		assertEquals(12, finding.priority().score());
		assertEquals(List.of("Owner"), finding.affectedClasses());
		assertEquals(List.of("Owner has 13 methods"), finding.evidence());
		assertEquals("Not currently a god class, but should be monitored if domain behavior expands.", finding.impact());
		assertEquals("Do not refactor now. Reassess if method count, responsibilities, or business rules increase.", finding.recommendation());
	}

	@Test
	void createsListEndpointWithoutPaginationFinding() {
		var observation = new ArchitectureObservation(
				"LIST_ENDPOINT_WITHOUT_PAGINATION",
				"OwnerController",
				"GET /owners may return multiple owners without explicit pagination.",
				60,
				true,
				FindingConfidence.POSSIBLE,
				FindingSeverity.LOW
		);

		var findings = builder.build(List.of(observation));

		assertEquals(1, findings.size());
		var finding = findings.get(0);
		assertEquals("List endpoint without pagination", finding.title());
		assertEquals(FindingSeverity.LOW, finding.severity());
		assertEquals(FindingConfidence.POSSIBLE, finding.confidence());
		assertEquals(FindingCategory.SCALABILITY, finding.category());
		assertEquals(12, finding.priority().score());
		assertEquals(List.of("OwnerController"), finding.affectedClasses());
		assertEquals(List.of("GET /owners may return multiple owners without explicit pagination."), finding.evidence());
		assertEquals("Fine for sample/small apps, but can become inefficient with larger datasets.", finding.impact());
	}

	@Test
	void calculatesHighConfirmedCircularDependencyPriority() {
		var observation = new ArchitectureObservation(
				"CIRCULAR_DEPENDENCY",
				"OrderService",
				"OrderService and PaymentService depend on each other.",
				95,
				false,
				FindingConfidence.CONFIRMED,
				FindingSeverity.HIGH
		);

		var finding = builder.build(List.of(observation)).get(0);

		assertEquals("Circular dependency", finding.title());
		assertEquals(FindingCategory.ARCHITECTURE, finding.category());
		assertEquals(75, finding.priority().score());
	}

	@Test
	void usesEarlyStageSimpleCrudRecommendationForControllerRepositoryAccess() {
		var observation = new ArchitectureObservation(
				"LAYERING",
				"OwnerController",
				"OwnerController accesses OwnerRepository directly. Determine whether this is an intentional architectural choice or a layering concern.",
				60,
				true,
				FindingConfidence.POSSIBLE,
				FindingSeverity.LOW
		);

		var finding = builder.build(
				List.of(observation),
				ArchitectureMaturity.EARLY_STAGE,
				ArchitectureStyle.SIMPLE_CRUD
		).get(0);

		assertEquals("Controller-to-repository access", finding.title());
		assertEquals("No meaningful impact.", finding.findingImpact().shortTermImpact());
		assertEquals("Business rules may become duplicated across controllers.", finding.findingImpact().longTermImpact());
		assertEquals(RiskLevel.LOW, finding.findingImpact().growthRisk());
		assertEquals("Current design is appropriate for an early-stage CRUD application. No meaningful short-term impact.",
				finding.impact());
		assertEquals("Avoid premature abstraction. Keep the current design until business rules become reusable, transactional, or shared.",
				finding.recommendation());
	}
}
