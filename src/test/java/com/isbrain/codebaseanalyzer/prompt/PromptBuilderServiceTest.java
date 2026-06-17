package com.isbrain.codebaseanalyzer.prompt;

import com.isbrain.codebaseanalyzer.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderServiceTest {

	private PromptBuilderService promptBuilder;

	@BeforeEach
	void setUp() {
		promptBuilder = new PromptBuilderService();
	}

	private ProjectAnalysisResult emptyResult() {
		return new ProjectAnalysisResult(
				new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
				List.of(), List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of(), new ArchitectureRiskScore(0, 0, 0, 0, 0),
				new ArchitectureScoreGuidance(Map.of(), List.of(), "No guidance."), List.of(), List.of(), ""
		);
	}

	private ProjectAnalysisResult resultWith(AnalysisSummary summary, List<String> relationships,
											 List<EndpointAnalysis> endpoints, List<PackageAnalysis> packages,
											 List<ClassMetrics> classMetrics,
											 List<ArchitecturalHotspot> hotspots,
											 List<ArchitectureObservation> observations,
											 List<String> couplingRanking) {
		return new ProjectAnalysisResult(
				summary, relationships, endpoints, packages, List.of(),
				classMetrics, hotspots, List.of(), observations, new ArchitectureRiskScore(15, 20, 25, 30, 35),
				new ArchitectureScoreGuidance(Map.of("layering", 7, "dependencies", 7), List.of("test rule"), "Test guidance."),
				List.of(new EvidenceBasedFinding(
						"Controller-to-repository access",
						FindingSeverity.LOW,
						FindingConfidence.POSSIBLE,
						List.of("OwnerController"),
						List.of("OwnerController -> OwnerRepository"),
						"Impact.",
						"Recommendation."
				)),
				couplingRanking, ""
		);
	}

	@Nested
	class PromptStructure {

		@Test
		void containsSystemRoleInstruction() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			assertTrue(prompt.contains("You are a senior software architect"));
		}

		@Test
		void containsAllSectionHeaders() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			assertTrue(prompt.contains("Architecture Summary:"));
			assertTrue(prompt.contains("Class Relationships:"));
			assertTrue(prompt.contains("REST Endpoints:"));
			assertTrue(prompt.contains("Package Structure:"));
			assertTrue(prompt.contains("Architectural Hotspots"));
			assertTrue(prompt.contains("Architecture Observations"));
			assertTrue(prompt.contains("Top Coupled Classes"));
			assertTrue(prompt.contains("Class Metrics"));
			assertTrue(prompt.contains("Calculated Risk Scores"));
			assertTrue(prompt.contains("Score Alignment Guidance"));
			assertTrue(prompt.contains("Evidence-Based Findings"));
		}

		@Test
		void containsJsonResponseFormat() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			assertTrue(prompt.contains("\"overallAssessment\""));
			assertTrue(prompt.contains("\"architectureScore\""));
			assertTrue(prompt.contains("\"categoryScores\""));
			assertTrue(prompt.contains("\"observationAnalysis\""));
			assertTrue(prompt.contains("\"keyConcerns\""));
			assertTrue(prompt.contains("\"findings\""));
		}

		@Test
		void containsScoringGuide() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			assertTrue(prompt.contains("Scoring guide:"));
			assertTrue(prompt.contains("1-3: Critical issues"));
		}
	}

	@Nested
	class SummaryFormatting {

		@Test
		void includesComponentCounts() {
			var summary = new AnalysisSummary(2, 3, 1, 1, 1, 2, 0, 0);
			var result = resultWith(summary, List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of(), List.of());
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("Controllers: 2"));
			assertTrue(prompt.contains("Services: 3"));
			assertTrue(prompt.contains("Repositories: 1"));
			assertTrue(prompt.contains("Entities: 2"));
		}

		@Test
		void includesAllComponentTypes() {
			var summary = new AnalysisSummary(1, 2, 3, 4, 5, 6, 7, 8);
			var result = resultWith(summary, List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of(), List.of());
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("Components: 4"));
			assertTrue(prompt.contains("Configurations: 5"));
			assertTrue(prompt.contains("ControllerAdvices: 7"));
			assertTrue(prompt.contains("RestControllerAdvices: 8"));
		}
	}

	@Nested
	class RelationshipFormatting {

		@Test
		void formatsRelationships() {
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of("Controller -> Service", "Service -> Repository"),
					List.of(), List.of(),
					List.of(), List.of(), List.of(), List.of());
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("Controller -> Service"));
			assertTrue(prompt.contains("Service -> Repository"));
		}

		@Test
		void showsNoneWhenNoRelationships() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			int sectionStart = prompt.indexOf("Class Relationships:");
			int sectionEnd = prompt.indexOf("REST Endpoints:");
			String section = prompt.substring(sectionStart, sectionEnd);
			assertTrue(section.contains("None"));
		}
	}

	@Nested
	class EndpointFormatting {

		@Test
		void formatsEndpoints() {
			var endpoints = List.of(
					new EndpointAnalysis("UserController", "/users", "GET", "/users", "getAll"),
					new EndpointAnalysis("UserController", "/users", "POST", "/users", "create")
			);
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), endpoints, List.of(),
					List.of(), List.of(), List.of(), List.of());
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("GET /users -> UserController.getAll()"));
			assertTrue(prompt.contains("POST /users -> UserController.create()"));
		}

		@Test
		void showsNoneWhenNoEndpoints() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			int sectionStart = prompt.indexOf("REST Endpoints:");
			int sectionEnd = prompt.indexOf("Package Structure:");
			String section = prompt.substring(sectionStart, sectionEnd);
			assertTrue(section.contains("None"));
		}
	}

	@Nested
	class PackageFormatting {

		@Test
		void formatsPackages() {
			var packages = List.of(
					new PackageAnalysis("com.app.controller", 2,
							Map.of(ComponentType.REST_CONTROLLER, 2L))
			);
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), packages,
					List.of(), List.of(), List.of(), List.of());
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("com.app.controller (2 classes)"));
		}

		@Test
		void showsNoneWhenNoPackages() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			int sectionStart = prompt.indexOf("Package Structure:");
			int sectionEnd = prompt.indexOf("Architectural Hotspots");
			String section = prompt.substring(sectionStart, sectionEnd);
			assertTrue(section.contains("None"));
		}
	}

	@Nested
	class ObservationFormatting {

		@Test
		void formatsObservations() {
			var observations = List.of(
					new ArchitectureObservation("LAYERING", "OwnerController",
							"OwnerController accesses OwnerRepository directly.", 60, true)
			);
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), List.of(), observations, List.of());
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("LAYERING"));
			assertTrue(prompt.contains("OwnerController"));
			assertTrue(prompt.contains("confidence: 60"));
			assertTrue(prompt.contains("findingConfidence: POSSIBLE"));
			assertTrue(prompt.contains("severity: MEDIUM"));
			assertTrue(prompt.contains("heuristic: yes"));
		}

		@Test
		void showsNoObservationsWhenEmpty() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			assertTrue(prompt.contains("No observations."));
		}
	}

	@Nested
	class HotspotFormatting {

		@Test
		void formatsHotspots() {
			var hotspots = List.of(
					new ArchitecturalHotspot("OrderService", "HIGH_COUPLING", 8,
							"OrderService depends on 8 collaborators.")
			);
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), hotspots, List.of(), List.of());
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("HIGH_COUPLING"));
			assertTrue(prompt.contains("OrderService"));
			assertTrue(prompt.contains("risk: 8"));
		}

		@Test
		void showsNoHotspotsWhenEmpty() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			assertTrue(prompt.contains("No hotspots detected."));
		}
	}

	@Nested
	class ClassMetricsFormatting {

		@Test
		void formatsClassMetrics() {
			var metrics = List.of(
					new ClassMetrics("OrderService", "com.app.service", ComponentType.SERVICE,
							22, 14, 10, 8, 8, 540)
			);
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					metrics, List.of(), List.of(), List.of());
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("OrderService"));
			assertTrue(prompt.contains("methods=22"));
			assertTrue(prompt.contains("publicMethods=14"));
			assertTrue(prompt.contains("dependencies=8"));
			assertTrue(prompt.contains("lines=540"));
		}
	}

	@Nested
	class CouplingFormatting {

		@Test
		void formatsCouplingRanking() {
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of(),
					List.of("ProjectScannerService: coupling score 8"));
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("ProjectScannerService: coupling score 8"));
		}
	}

	@Nested
	class RiskScoreFormatting {

		@Test
		void formatsCalculatedRiskScores() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			assertTrue(prompt.contains("Layering Risk: 0"));
			assertTrue(prompt.contains("Coupling Risk: 0"));
			assertTrue(prompt.contains("Modularity Risk: 0"));
			assertTrue(prompt.contains("Maintainability Risk: 0"));
			assertTrue(prompt.contains("Scalability Risk: 0"));
		}

		@Test
		void tellsModelToUseCalculatedRiskScores() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult());
			assertTrue(prompt.contains("Use the calculated risk scores when determining category scores"));
			assertTrue(prompt.contains("A simple architecture is not automatically a poor architecture"));
		}

		@Test
		void includesScoreAlignmentRulesAndEvidenceFindings() {
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of(), List.of()
			);
			var prompt = promptBuilder.buildArchitecturePrompt(result);
			assertTrue(prompt.contains("layering >= 7"));
			assertTrue(prompt.contains("test rule"));
			assertTrue(prompt.contains("Controller-to-repository access"));
			assertTrue(prompt.contains("OwnerController -> OwnerRepository"));
		}
	}
}
