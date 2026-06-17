package com.isbrain.codebaseanalyzer.prompt;

import com.isbrain.codebaseanalyzer.model.AnalysisSummary;
import com.isbrain.codebaseanalyzer.model.ArchitecturalHotspot;
import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ClassMetrics;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.PackageAnalysis;
import com.isbrain.codebaseanalyzer.model.ProjectAnalysisResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PromptBuilderService {

	public String buildArchitecturePrompt(ProjectAnalysisResult result) {
		String summary = formatSummary(result.summary());
		String relationships = formatRelationships(result.relationships());
		String endpoints = formatEndpoints(result.endpoints());
		String packages = formatPackages(result.packages());
		String observationsText = formatObservations(result.observations());
		String couplingRankingText = formatList(result.couplingRanking());
		String classMetricsText = formatClassMetrics(result.classMetrics());
		String hotspotsText = formatHotspots(result.hotspots());
		String riskScoreText = formatRiskScore(result.riskScore());
		String scoreGuidanceText = formatScoreGuidance(result.scoreGuidance());
		String evidenceFindingsText = formatEvidenceFindings(result.evidenceBasedFindings());

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
				
				Detected Architecture Style:
				%s
				
				Architectural Hotspots (heuristically identified risk areas):
				%s
				
				Architecture Observations (heuristic signals, not confirmed defects):
				%s
				
				Top Coupled Classes (high coupling = maintenance risk):
				%s
				
				Class Metrics (per-class size and complexity indicators):
				%s
				
				Calculated Risk Scores (0 = no risk, 100 = severe risk):
				%s
				
				Score Alignment Guidance:
				%s
				
				Evidence-Based Findings:
				%s
				
				Analyze the metadata above. For every observation, reference the specific class names involved and explain the impact. Be specific - do not give generic advice.
				
				Not every observation is a problem. Observations are heuristic signals, not confirmed defects. Determine for each observation:
				- whether the pattern is actually problematic
				- whether it is acceptable given the project's size, architectural style, domain complexity, or framework conventions
				- whether it represents technical debt
				- whether future growth may turn it into a problem
				
				Avoid recommending additional layers unless they provide clear architectural value. Not every controller needs a service. Not every project needs a repository pattern.
				Do not penalize a project merely for being small or intentionally simple. Evaluate whether architectural choices are appropriate for the project's size and complexity. A simple architecture is not automatically a poor architecture.
				
				Pay special attention to Architectural Hotspots. These hotspots are heuristically identified risk areas and may indicate deeper design issues even if no formal architecture violation has been detected. Analyze whether each hotspot reflects responsibility creep, orchestration overload, excessive coupling, poor cohesion, maintainability concerns, or scalability risks.
				Use the calculated risk scores when determining category scores. Do not invent scores that significantly contradict the calculated risks unless strong evidence exists in the metadata.
				Use the score alignment guidance as a floor for category scores unless there is stronger contradictory evidence in confirmed high-severity findings.
				If the project is small, CRUD-oriented, has no circular dependencies, no significant hotspots, and only possible/low-severity concerns, prefer this overall assessment wording: "The architecture demonstrates a simple, reasonable structure for a small CRUD-oriented Spring Boot application, with low-severity risks that should be monitored if the project grows."
				Findings already include calculated priority scores. Higher priority findings should dominate key concerns and recommended refactorings. Do not let low-priority findings dominate the report.
				If Detected Architecture Style is SIMPLE_CRUD: direct controller-to-repository access is LOW/POSSIBLE, missing service layer is not automatically a problem, pagination only matters for real collection/list endpoints, and recommendations should avoid large architectural refactors.
				
				Prioritize analysis of:
				- Architecture layering quality
				- Dependency quality and coupling
				- Modularity and single responsibility
				- Maintainability risks
				- Scalability concerns
				
				Do not limit the review to these areas.
				Identify any additional architectural, design, performance, security, testing, operational, maintainability, or production-readiness concerns that are relevant.
				
				Scoring guide:
				- 1-3: Critical issues, architecture is broken
				- 4-5: Significant problems, needs major refactoring
				- 6-7: Decent structure with notable issues
				- 8-9: Well-architected with minor improvements possible
				- 10: Exceptional, production-grade architecture
				
				High-confidence observations (confidence >= 80) that are confirmed as problematic should significantly impact scoring. Low-confidence heuristic observations should inform the review but not dominate scores.
				Use the finding severity and finding confidence fields to separate Confirmed Issues from Potential Risks. Do not describe LOW or INFO severity observations as critical issues.
				
				Respond ONLY with a valid JSON object, no markdown, no explanation outside the JSON:
				{
				  "overallAssessment": "...",
				  "architectureScore": 7,
				  "strengths": ["...", "..."],
				  "keyConcerns": ["...", "..."],
				  "improvements": ["...", "..."],
				  "categoryScores": {
				    "layering": 7,
				    "modularity": 7,
				    "dependencies": 8,
				    "maintainability": 7,
				    "scalability": 6
				  },
				  "observationAnalysis": [
				    "LAYERING: OwnerController accesses OwnerRepository directly. This pattern is acceptable in this CRUD-oriented application given its size and simplicity.",
				    "CIRCULAR_DEPENDENCY: OrderService and NotificationService depend on each other. Extract shared logic into a new service.",
				    "GOD_CLASS: MegaService has 8 dependencies and 520 LOC. Split by domain responsibility.",
				    "DOMAIN_ENTITY_COMPLEXITY: Owner has 13 methods. This is a low-severity possible risk, not a confirmed god class.",
				    "ORPHAN_SERVICE: ReportService is not injected anywhere. Remove or integrate into a workflow.",
				    "FAT_CONTROLLER: AdminController has 16 endpoints. Split by resource into smaller controllers.",
				    "EMPTY_CONTROLLER: HealthController has no endpoints. Add a health check or remove it."
				  ],
				  "findings": [
				    {
				      "title": "Controller-to-repository access",
				      "severity": "LOW",
				      "confidence": "POSSIBLE",
				      "category": "ARCHITECTURE",
				      "priority": {"score": 12},
				      "affectedClasses": ["OwnerController"],
				      "evidence": ["OwnerController -> OwnerRepository"],
				      "impact": "Direct repository access can reduce separation if business logic grows.",
				      "recommendation": "Keep the simple structure if it remains CRUD-oriented; introduce a service only when behavior becomes reusable or complex."
				    }
				  ]
				}
				""".formatted(summary, relationships, endpoints, packages, result.architectureStyle(),
						hotspotsText, observationsText,
						couplingRankingText, classMetricsText, riskScoreText,
						scoreGuidanceText, evidenceFindingsText);
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

	private String formatList(List<String> items) {
		return items.isEmpty()
				? "None"
				: String.join("\n", items);
	}

	private String formatPackages(List<PackageAnalysis> packages) {
		return packages.isEmpty()
				? "None"
				: packages.stream()
				.map(p -> "%s (%d classes) %s".formatted(
						p.packageName(), p.classCount(), p.componentTypeDistribution()))
				.collect(Collectors.joining("\n"));
	}

	private String formatObservations(List<ArchitectureObservation> observations) {
		if (observations == null || observations.isEmpty()) {
			return "No observations.";
		}
		return observations.stream()
				.map(o -> "[%s] %s (confidence: %d/100, findingConfidence: %s, severity: %s, heuristic: %s) - %s".formatted(
						o.type(), o.className(), o.confidence(),
						o.findingConfidence(), o.severity(),
						o.heuristic() ? "yes" : "no", o.description()))
				.collect(Collectors.joining("\n"));
	}

	private String formatHotspots(List<ArchitecturalHotspot> hotspots) {
		if (hotspots == null || hotspots.isEmpty()) {
			return "No hotspots detected.";
		}
		return hotspots.stream()
				.map(h -> "[%s] %s (risk: %d/10) - %s".formatted(
						h.type(), h.className(), h.riskScore(), h.explanation()))
				.collect(Collectors.joining("\n"));
	}

	private String formatClassMetrics(List<ClassMetrics> metrics) {
		if (metrics == null || metrics.isEmpty()) {
			return "None";
		}
		return metrics.stream()
				.map(m -> "%s (%s): methods=%d, publicMethods=%d, fields=%d, dependencies=%d, constructorParams=%d, lines=%d".formatted(
						m.className(), m.componentType(),
						m.methodCount(), m.publicMethodCount(), m.fieldCount(),
						m.dependencyCount(), m.constructorParameterCount(), m.lineCount()))
				.collect(Collectors.joining("\n"));
	}

	private String formatRiskScore(com.isbrain.codebaseanalyzer.model.ArchitectureRiskScore riskScore) {
		if (riskScore == null) {
			return "None";
		}
		return """
				Layering Risk: %d
				Coupling Risk: %d
				Modularity Risk: %d
				Maintainability Risk: %d
				Scalability Risk: %d"""
				.formatted(
						riskScore.layeringRisk(),
						riskScore.couplingRisk(),
						riskScore.modularityRisk(),
						riskScore.maintainabilityRisk(),
						riskScore.scalabilityRisk()
				);
	}

	private String formatScoreGuidance(com.isbrain.codebaseanalyzer.model.ArchitectureScoreGuidance scoreGuidance) {
		if (scoreGuidance == null) {
			return "None";
		}
		String minimumScores = scoreGuidance.minimumCategoryScores().isEmpty()
				? "None"
				: scoreGuidance.minimumCategoryScores().entrySet().stream()
				.map(entry -> "%s >= %d".formatted(entry.getKey(), entry.getValue()))
				.collect(Collectors.joining("\n"));
		String rules = scoreGuidance.rulesApplied().isEmpty()
				? "No score alignment rules applied."
				: String.join("\n", scoreGuidance.rulesApplied());
		return """
				Minimum Category Scores:
				%s
				Rules Applied:
				%s
				Assessment Guidance:
				%s"""
				.formatted(minimumScores, rules, scoreGuidance.assessmentGuidance());
	}

	private String formatEvidenceFindings(List<com.isbrain.codebaseanalyzer.model.EvidenceBasedFinding> findings) {
		if (findings == null || findings.isEmpty()) {
			return "None";
		}
		return findings.stream()
				.map(finding -> "%s (severity: %s, confidence: %s, category: %s, priority: %d) affectedClasses=%s evidence=%s impact=%s recommendation=%s".formatted(
						finding.title(),
						finding.severity(),
						finding.confidence(),
						finding.category(),
						finding.priority().score(),
						finding.affectedClasses(),
						finding.evidence(),
						finding.impact(),
						finding.recommendation()))
				.collect(Collectors.joining("\n"));
	}
}
