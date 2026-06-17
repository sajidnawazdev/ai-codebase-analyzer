package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.EvidenceBasedFinding;
import com.isbrain.codebaseanalyzer.model.FindingCategory;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingPriority;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceFindingBuilder {

	public List<EvidenceBasedFinding> build(List<ArchitectureObservation> observations) {
		Map<FindingKey, List<ArchitectureObservation>> observationsByFinding = new LinkedHashMap<>();
		for (ArchitectureObservation observation : observations) {
			observationsByFinding.computeIfAbsent(toFindingKey(observation), ignored -> new ArrayList<>())
					.add(observation);
		}
		return observationsByFinding.entrySet()
				.stream()
				.map(entry -> toFinding(entry.getKey(), entry.getValue()))
				.toList();
	}

	private EvidenceBasedFinding toFinding(FindingKey key, List<ArchitectureObservation> observations) {
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

		return switch (key.title()) {
			case "Controller-to-repository access" -> new EvidenceBasedFinding(
					key.title(),
					severity,
					confidence,
					affectedClasses,
					evidence,
					"Acceptable for this small CRUD application, but business logic may become harder to centralize if the project grows.",
					"Keep current structure for now. Introduce application services only when business logic becomes reusable, transactional, or shared across controllers.",
					FindingCategory.ARCHITECTURE,
					priorityFor(severity, confidence)
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
					priorityFor(severity, confidence)
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
					priorityFor(severity, confidence)
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
					priorityFor(severity, confidence)
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
					priorityFor(severity, confidence)
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
					priorityFor(severity, confidence)
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
					priorityFor(severity, confidence)
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
					priorityFor(severity, confidence)
			);
		};
	}

	private FindingPriority priorityFor(FindingSeverity severity, FindingConfidence confidence) {
		return EvidenceBasedFinding.priorityFor(severity, confidence);
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
