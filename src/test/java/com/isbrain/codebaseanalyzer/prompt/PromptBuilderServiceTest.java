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
				List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), List.of(), ""
		);
	}

	private ProjectAnalysisResult resultWith(AnalysisSummary summary, List<String> relationships,
											 List<EndpointAnalysis> endpoints, List<PackageAnalysis> packages,
											 List<String> violations, List<String> circularDeps,
											 List<String> godClasses, List<String> emptyControllers,
											 List<String> orphanServices, List<String> fatControllers,
											 List<String> couplingRanking) {
		return new ProjectAnalysisResult(
				summary, relationships, endpoints, packages, List.of(),
				violations, circularDeps, godClasses, emptyControllers, orphanServices,
				fatControllers, couplingRanking, ""
		);
	}

	@Nested
	class PromptStructure {

		@Test
		void containsSystemRoleInstruction() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult(), List.of());

			assertTrue(prompt.contains("You are a senior software architect"));
		}

		@Test
		void containsAllSectionHeaders() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult(), List.of());

			assertTrue(prompt.contains("Architecture Summary:"));
			assertTrue(prompt.contains("Class Relationships:"));
			assertTrue(prompt.contains("REST Endpoints:"));
			assertTrue(prompt.contains("Package Structure:"));
			assertTrue(prompt.contains("Detected Layer Violations:"));
			assertTrue(prompt.contains("Detected Circular Dependencies:"));
			assertTrue(prompt.contains("Detected God Classes:"));
			assertTrue(prompt.contains("Detected Empty Controllers:"));
			assertTrue(prompt.contains("Detected Orphan Services:"));
			assertTrue(prompt.contains("Detected Fat Controllers:"));
			assertTrue(prompt.contains("Top Coupled Classes"));
		}

		@Test
		void containsJsonResponseFormat() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult(), List.of());

			assertTrue(prompt.contains("\"overallAssessment\""));
			assertTrue(prompt.contains("\"architectureScore\""));
			assertTrue(prompt.contains("\"categoryScores\""));
		}

		@Test
		void containsScoringGuide() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult(), List.of());

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
					List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("Controllers: 2"));
			assertTrue(prompt.contains("Services: 3"));
			assertTrue(prompt.contains("Repositories: 1"));
			assertTrue(prompt.contains("Entities: 2"));
		}

		@Test
		void includesAllComponentTypes() {
			var summary = new AnalysisSummary(1, 2, 3, 4, 5, 6, 7, 8);
			var result = resultWith(summary, List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

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
					List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("Controller -> Service"));
			assertTrue(prompt.contains("Service -> Repository"));
		}

		@Test
		void showsNoneWhenNoRelationships() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult(), List.of());

			// The "Class Relationships:" section should contain "None"
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
					List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("GET /users -> UserController.getAll()"));
			assertTrue(prompt.contains("POST /users -> UserController.create()"));
		}

		@Test
		void showsNoneWhenNoEndpoints() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult(), List.of());

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
					List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("com.app.controller (2 classes)"));
		}

		@Test
		void showsNoneWhenNoPackages() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult(), List.of());

			int sectionStart = prompt.indexOf("Package Structure:");
			int sectionEnd = prompt.indexOf("Detected Layer Violations:");
			String section = prompt.substring(sectionStart, sectionEnd);

			assertTrue(section.contains("None"));
		}
	}

	@Nested
	class ViolationFormatting {

		@Test
		void formatsLayerViolations() {
			var violations = List.of("Controller -> Repository: bypasses service layer");

			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult(), violations);

			assertTrue(prompt.contains("Controller -> Repository: bypasses service layer"));
		}

		@Test
		void showsNoViolationsDetectedWhenEmpty() {
			var prompt = promptBuilder.buildArchitecturePrompt(emptyResult(), List.of());

			int sectionStart = prompt.indexOf("Detected Layer Violations:");
			int sectionEnd = prompt.indexOf("Detected Circular Dependencies:");
			String section = prompt.substring(sectionStart, sectionEnd);

			assertTrue(section.contains("No violations detected."));
		}

		@Test
		void formatsCircularDependencies() {
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), List.of("Circular: A -> B -> A"), List.of(),
					List.of(), List.of(), List.of(), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("Circular: A -> B -> A"));
		}

		@Test
		void formatsGodClasses() {
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of("God class: MegaService — 12 methods"),
					List.of(), List.of(), List.of(), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("God class: MegaService"));
		}

		@Test
		void formatsEmptyControllers() {
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of(),
					List.of("Empty controller: HealthController"), List.of(), List.of(), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("Empty controller: HealthController"));
		}

		@Test
		void formatsOrphanServices() {
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of(), List.of(),
					List.of("Orphan service: DeadService"), List.of(), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("Orphan service: DeadService"));
		}

		@Test
		void formatsFatControllers() {
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of(), List.of(), List.of(),
					List.of("Fat controller: AdminController — 11 endpoints"), List.of());

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("Fat controller: AdminController"));
		}

		@Test
		void formatsCouplingRanking() {
			var result = resultWith(
					new AnalysisSummary(0, 0, 0, 0, 0, 0, 0, 0),
					List.of(), List.of(), List.of(),
					List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
					List.of("ProjectScannerService: coupling score 8"));

			var prompt = promptBuilder.buildArchitecturePrompt(result, List.of());

			assertTrue(prompt.contains("ProjectScannerService: coupling score 8"));
		}
	}
}
