package com.isbrain.codebaseanalyzer.prompt;

import com.isbrain.codebaseanalyzer.model.AnalysisSummary;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.PackageAnalysis;
import com.isbrain.codebaseanalyzer.model.ProjectAnalysisResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PromptBuilderService {

	public String buildArchitecturePrompt(ProjectAnalysisResult result, List<String> violations) {
		String summary = formatSummary(result.summary());
		String relationships = formatRelationships(result.relationships());
		String endpoints = formatEndpoints(result.endpoints());
		String packages = formatPackages(result.packages());
		String violationsText = formatViolations(violations);
		String circularDependenciesText = formatViolations(result.circularDependencies());
		String godClassesText = formatViolations(result.godClasses());
		String emptyControllersText = formatViolations(result.emptyControllers());
		String orphanServicesText = formatViolations(result.orphanServices());
		String fatControllersText = formatViolations(result.fatControllers());
		String couplingRankingText = formatViolations(result.couplingRanking());

		return """
				You are a senior software architect specializing in Spring Boot microservices.
				
				Your task is to analyze the architecture quality of a Java/Spring Boot project based on structured metadata extracted from its source code.
				
				Do NOT assume every project needs a persistence layer, repositories, entities, or database integration. Evaluate the architecture based on what exists, not what is missing by convention.
				
				Orchestrator services (services that coordinate multiple other services) are expected to have higher coupling scores. A service with 4-5 dependencies is acceptable if it acts as a coordinator. Only flag coupling as a problem when a non-orchestrating service (e.g. a utility, extractor, or builder) has 4+ dependencies, or when an orchestrator exceeds 6+ dependencies.
				
				Architecture Summary:
				%s
				
				Class Relationships:
				%s
				
				REST Endpoints:
				%s
				
				Package Structure:
				%s
				
				Detected Layer Violations:
				%s
				
				Detected Circular Dependencies:
				%s
				
				Detected God Classes:
				%s
				
				Detected Empty Controllers:
				%s
				
				Detected Orphan Services:
				%s
				
				Detected Fat Controllers:
				%s
				
				Top Coupled Classes (high coupling = maintenance risk):
				%s
				
				Analyze the metadata above. For every detected violation, reference the specific class names involved and explain the impact. Be specific — do not give generic advice.
				
				Focus on:
				- Architecture layering quality
				- Dependency quality and coupling
				- Modularity and single responsibility
				- Maintainability risks
				- Scalability concerns
				- Concrete improvements with class names
				
				Scoring guide:
				- 1-3: Critical issues, architecture is broken
				- 4-5: Significant problems, needs major refactoring
				- 6-7: Decent structure with notable issues
				- 8-9: Well-architected with minor improvements possible
				- 10: Exceptional, production-grade architecture
				
				A project with layer violations, god classes, or circular dependencies should NEVER score above 5 in affected categories.
				
				Respond ONLY with a valid JSON object, no markdown, no explanation outside the JSON:
				{
				  "overallAssessment": "...",
				  "architectureScore": 7,
				  "strengths": ["...", "..."],
				  "criticalIssues": ["...", "..."],
				  "improvements": ["...", "..."],
				  "categoryScores": {
				    "layering": 7,
				    "modularity": 5,
				    "dependencies": 6,
				    "maintainability": 7,
				    "scalability": 6
				  },
				  "layerViolationAnalysis": [
				    "OrderController directly accesses OrderRepository, bypassing OrderService. Route through the service layer."
				  ],
				  "circularDependencyAnalysis": [
				    "OrderService and NotificationService depend on each other. Extract shared logic into a new service."
				  ],
				  "godClassAnalysis": [
				    "MegaService has 7 dependencies and 14 methods. Split by domain responsibility."
				  ],
				  "orphanServiceAnalysis": [
				    "ReportService is not injected anywhere. Remove or integrate into a workflow."
				  ],
				  "fatControllerAnalysis": [
				    "AdminController has 11 endpoints. Split by resource into smaller controllers."
				  ],
				  "emptyControllerAnalysis": [
				    "HealthController has no endpoints. Add a health check or remove it."
				  ]
				}
				""".formatted(summary, relationships, endpoints, packages, violationsText,
						circularDependenciesText, godClassesText, emptyControllersText,
						orphanServicesText, fatControllersText, couplingRankingText);
	}

	private String formatSummary(AnalysisSummary summary) {
		return """
				Controllers: %d, Services: %d, Repositories: %d, \
				Components: %d, Configurations: %d, Entities: %d, \
				ControllerAdvices: %d, RestControllerAdvices: %d"""
				.formatted(
						summary.controllers(), summary.services(), summary.repositories(),
						summary.components(), summary.configurations(), summary.entities(),
						summary.controllerAdvices(), summary.restControllerAdvices()
				);
	}

	private String formatRelationships(List<String> relationships) {
		return relationships.isEmpty()
				? "None"
				: String.join("\n", relationships);
	}

	private String formatEndpoints(List<EndpointAnalysis> endpoints) {
		return endpoints.isEmpty()
				? "None"
				: endpoints.stream()
				.map(e -> "%s %s -> %s.%s()".formatted(
						e.httpMethod(), e.endpointPath(), e.controllerName(), e.methodName()))
				.collect(Collectors.joining("\n"));
	}

	private String formatViolations(List<String> violations) {
		return violations.isEmpty()
				? "No violations detected."
				: String.join("\n", violations);
	}

	private String formatPackages(List<PackageAnalysis> packages) {
		return packages.isEmpty()
				? "None"
				: packages.stream()
				.map(p -> "%s (%d classes) %s".formatted(
						p.packageName(), p.classCount(), p.componentTypeDistribution()))
				.collect(Collectors.joining("\n"));
	}
}
