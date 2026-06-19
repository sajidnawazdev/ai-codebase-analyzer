package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ArchitectureMaturity;
import com.isbrain.codebaseanalyzer.model.ArchitectureStyle;
import com.isbrain.codebaseanalyzer.model.EvidenceBasedFinding;
import com.isbrain.codebaseanalyzer.model.FindingCategory;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingImpact;
import com.isbrain.codebaseanalyzer.model.FindingPriority;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import com.isbrain.codebaseanalyzer.model.RiskLevel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceFindingBuilder {

	public List<EvidenceBasedFinding> build(List<ArchitectureObservation> observations) {
		return build(observations, ArchitectureMaturity.PROTOTYPE, ArchitectureStyle.UNKNOWN);
	}

	public List<EvidenceBasedFinding> build(
			List<ArchitectureObservation> observations,
			ArchitectureMaturity maturity,
			ArchitectureStyle architectureStyle
	) {
		Map<FindingKey, List<ArchitectureObservation>> observationsByFinding = new LinkedHashMap<>();
		for (ArchitectureObservation observation : observations) {
			observationsByFinding.computeIfAbsent(toFindingKey(observation), ignored -> new ArrayList<>())
					.add(observation);
		}
		return observationsByFinding.entrySet()
				.stream()
				.map(entry -> toFinding(entry.getKey(), entry.getValue(), maturity, architectureStyle))
				.toList();
	}

	private EvidenceBasedFinding toFinding(
			FindingKey key,
			List<ArchitectureObservation> observations,
			ArchitectureMaturity maturity,
			ArchitectureStyle architectureStyle
	) {
		FindingSeverity severity = strongestSeverity(observations);
		FindingConfidence confidence = strongestConfidence(observations);
		List<String> affectedClasses = observations.stream()
				.map(ArchitectureObservation::className)
				.distinct()
				.toList();
		List<String> evidence = observations.stream()
				.map(this::extractEvidence)
				.distinct()
				.toList();
		FindingImpact findingImpact = impactFor(key.title());

		return switch (key.title()) {
			case "Controller-to-repository access" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					controllerRepositoryImpact(maturity, architectureStyle),
					controllerRepositoryRecommendation(maturity, architectureStyle),
					FindingCategory.ARCHITECTURE,
					priorityFor(severity, confidence),
					findingImpact
			);
			case "Layering direction concern" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Unexpected dependency direction can make layer responsibilities harder to preserve as the project grows.",
					"Review whether this dependency is intentional; if not, move the shared behavior behind an appropriate lower-level collaborator.",
					FindingCategory.ARCHITECTURE,
					priorityFor(severity, confidence),
					findingImpact
			);
			case "Circular dependency" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Cycles increase coupling and can make testing, initialization, and future refactoring harder.",
					"Break the cycle by moving shared behavior behind a separate collaborator or reversing one dependency.",
					FindingCategory.ARCHITECTURE,
					priorityFor(severity, confidence),
					findingImpact
			);
			case "Large multi-responsibility class" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Large classes with many methods, dependencies, or high LOC are harder to test and change safely.",
					"Split responsibilities along domain workflow boundaries when the class starts accumulating unrelated behavior.",
					FindingCategory.MAINTAINABILITY,
					priorityFor(severity, confidence),
					findingImpact
			);
			case "Domain entity complexity" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Not currently a god class, but should be monitored if domain behavior expands.",
					"Do not refactor now. Reassess if method count, responsibilities, or business rules increase.",
					FindingCategory.MAINTAINABILITY,
					priorityFor(severity, confidence),
					findingImpact
			);
			case "Controller responsibility concentration" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Controllers with many endpoints, dependencies, or high LOC can become hard to navigate and test.",
					"Split by resource or workflow when endpoint count, dependencies, or request handling logic grows.",
					FindingCategory.DESIGN,
					priorityFor(severity, confidence),
					findingImpact
			);
			case "List endpoint without pagination" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Fine for sample/small apps, but can become inefficient with larger datasets.",
					"Keep as-is for small datasets. Add pagination when the endpoint can return many records or when the backing table grows.",
					FindingCategory.SCALABILITY,
					priorityFor(severity, confidence),
					findingImpact
			);
			case "Field injection" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Field injection reduces testability. Prefer constructor injection.",
					"Inject required collaborators through constructors so dependencies are explicit and tests can instantiate the class directly.",
					FindingCategory.DESIGN,
					priorityFor(severity, confidence),
					findingImpact
			);
			case "Missing transaction boundary on write method" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Write operation has unclear transaction boundary.",
					"Add @Transactional at the service method or service class boundary when the write operation must be atomic.",
					FindingCategory.DESIGN,
					priorityFor(severity, confidence),
					findingImpact
			);
			case "Repository method explosion" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Repository may be accumulating query responsibility.",
					"Consider moving complex query composition into a focused query service or splitting repository responsibilities by aggregate/use case.",
					FindingCategory.DESIGN,
					priorityFor(severity, confidence),
					findingImpact
			);
			default -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					observations.get(0).description(),
					"Review this observation in the context of project size and expected growth.",
					FindingCategory.DESIGN,
					priorityFor(severity, confidence),
					findingImpact
			);
		};
	}

	private FindingPriority priorityFor(FindingSeverity severity, FindingConfidence confidence) {
		return EvidenceBasedFinding.priorityFor(severity, confidence);
	}

	private String controllerRepositoryImpact(ArchitectureMaturity maturity, ArchitectureStyle architectureStyle) {
		if (architectureStyle == ArchitectureStyle.SIMPLE_CRUD
				&& (maturity == ArchitectureMaturity.PROTOTYPE || maturity == ArchitectureMaturity.EARLY_STAGE)) {
			return "Current design is appropriate for an early-stage CRUD application. No meaningful short-term impact.";
		}
		return "Acceptable for this CRUD-oriented application, but business logic may become harder to centralize if the project grows.";
	}

	private String controllerRepositoryRecommendation(ArchitectureMaturity maturity, ArchitectureStyle architectureStyle) {
		if (architectureStyle == ArchitectureStyle.SIMPLE_CRUD
				&& (maturity == ArchitectureMaturity.PROTOTYPE || maturity == ArchitectureMaturity.EARLY_STAGE)) {
			return "Avoid premature abstraction. Keep the current design until business rules become reusable, transactional, or shared.";
		}
		return "Keep current structure for now. Introduce application services only when business logic becomes reusable, transactional, or shared across controllers.";
	}

	private FindingImpact impactFor(String title) {
		return switch (title) {
			case "Controller-to-repository access" -> new FindingImpact(
					"No meaningful impact.",
					"Business rules may become duplicated across controllers.",
					RiskLevel.LOW
			);
			case "Layering direction concern" -> new FindingImpact(
					"Responsibilities may be harder to understand.",
					"Layer boundaries can erode as more dependencies follow the same direction.",
					RiskLevel.MEDIUM
			);
			case "Circular dependency" -> new FindingImpact(
					"Testing and initialization can become harder.",
					"Refactoring and module extraction become significantly riskier.",
					RiskLevel.HIGH
			);
			case "Large multi-responsibility class" -> new FindingImpact(
					"Changes take longer to reason about.",
					"Unrelated responsibilities can accumulate in one class and slow delivery.",
					RiskLevel.HIGH
			);
			case "Domain entity complexity" -> new FindingImpact(
					"No meaningful impact.",
					"Entity behavior may become harder to isolate if unrelated rules accumulate.",
					RiskLevel.LOW
			);
			case "Controller responsibility concentration" -> new FindingImpact(
					"Controller changes may require more careful testing.",
					"Request handling, orchestration, and validation may become tangled.",
					RiskLevel.MEDIUM
			);
			case "List endpoint without pagination" -> new FindingImpact(
					"No meaningful impact for small datasets.",
					"Large datasets can increase response time and memory usage.",
					RiskLevel.LOW
			);
			case "Field injection" -> new FindingImpact(
					"Unit tests may require Spring or reflection-based setup.",
					"Hidden dependencies make refactoring and constructor-level validation harder.",
					RiskLevel.LOW
			);
			case "Missing transaction boundary on write method" -> new FindingImpact(
					"Write behavior may depend on caller context.",
					"Multi-step writes can become inconsistent when business workflows grow.",
					RiskLevel.MEDIUM
			);
			case "Repository method explosion" -> new FindingImpact(
					"No immediate runtime impact.",
					"Query responsibility can concentrate in one repository and become harder to evolve.",
					RiskLevel.LOW
			);
			default -> FindingImpact.none();
		};
	}

	private FindingKey toFindingKey(ArchitectureObservation observation) {
		return switch (observation.type()) {
			case "LAYERING" -> observation.description().contains(" accesses ")
					? new FindingKey("Controller-to-repository access")
					: new FindingKey("Layering direction concern");
			case "CIRCULAR_DEPENDENCY" -> new FindingKey("Circular dependency");
			case "GOD_CLASS" -> new FindingKey("Large multi-responsibility class");
			case "DOMAIN_ENTITY_COMPLEXITY" -> new FindingKey("Domain entity complexity");
			case "FAT_CONTROLLER" -> new FindingKey("Controller responsibility concentration");
			case "LIST_ENDPOINT_WITHOUT_PAGINATION" -> new FindingKey("List endpoint without pagination");
			case "FIELD_INJECTION" -> new FindingKey("Field injection");
			case "MISSING_TRANSACTIONAL_ON_WRITE_METHOD" -> new FindingKey("Missing transaction boundary on write method");
			case "REPOSITORY_METHOD_EXPLOSION" -> new FindingKey("Repository method explosion");
			default -> new FindingKey(observation.type());
		};
	}

	private String extractEvidence(ArchitectureObservation observation) {
		String description = observation.description();
		if (description.contains(" accesses ")) {
			String source = observation.className();
			String target = description.substring(description.indexOf(" accesses ") + " accesses ".length())
					.split(" ")[0]
					.replace(".", "");
			return source + " -> " + target;
		}
		if (description.contains(" and ") && description.contains(" depend on each other")) {
			String target = description.substring(description.indexOf(" and ") + " and ".length())
					.split(" ")[0]
					.replace(".", "");
			return observation.className() + " <-> " + target;
		}
		if (description.contains(" has ") && description.contains(" methods")) {
			int start = description.indexOf(" has ");
			int end = description.indexOf(" methods") + " methods".length();
			return observation.className() + description.substring(start, end);
		}
		if (observation.type().equals("LIST_ENDPOINT_WITHOUT_PAGINATION")) {
			return description;
		}
		if (observation.type().equals("FIELD_INJECTION")
				|| observation.type().equals("MISSING_TRANSACTIONAL_ON_WRITE_METHOD")
				|| observation.type().equals("REPOSITORY_METHOD_EXPLOSION")) {
			return observation.className() + ": " + description;
		}
		return observation.className();
	}

	private FindingSeverity strongestSeverity(List<ArchitectureObservation> observations) {
		return observations.stream()
				.map(ArchitectureObservation::severity)
				.min(Comparator.comparingInt(Enum::ordinal))
				.orElse(FindingSeverity.INFO);
	}

	private FindingConfidence strongestConfidence(List<ArchitectureObservation> observations) {
		return observations.stream()
				.map(ArchitectureObservation::findingConfidence)
				.min(Comparator.comparingInt(Enum::ordinal))
				.orElse(FindingConfidence.POSSIBLE);
	}

	private record FindingKey(String title) {
	}
}
