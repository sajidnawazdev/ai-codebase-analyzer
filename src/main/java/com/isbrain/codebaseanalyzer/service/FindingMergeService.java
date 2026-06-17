package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.EvidenceBasedFinding;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import com.isbrain.codebaseanalyzer.model.MergedFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FindingMergeService {

	public List<MergedFinding> merge(List<EvidenceBasedFinding> findings) {
		Map<String, List<EvidenceBasedFinding>> findingsByFingerprint = new LinkedHashMap<>();
		for (EvidenceBasedFinding finding : findings) {
			findingsByFingerprint.computeIfAbsent(fingerprint(finding), ignored -> new ArrayList<>())
					.add(finding);
		}

		return findingsByFingerprint.values()
				.stream()
				.map(this::toMergedFinding)
				.sorted(Comparator.comparingInt(MergedFinding::priority).reversed())
				.toList();
	}

	private MergedFinding toMergedFinding(List<EvidenceBasedFinding> findings) {
		Set<String> affectedClasses = new LinkedHashSet<>();
		List<String> evidence = new ArrayList<>();
		for (EvidenceBasedFinding finding : findings) {
			affectedClasses.addAll(finding.affectedClasses());
			for (String item : finding.evidence()) {
				if (!evidence.contains(item)) {
					evidence.add(item);
				}
			}
		}

		FindingSeverity severity = strongestSeverity(findings);
		FindingConfidence confidence = strongestConfidence(findings);
		String title = mergedTitle(findings.get(0));
		int priority = mergedPriority(findings, affectedClasses.size());

		if (title.equals("Controller-to-Repository Access Pattern")) {
			return new MergedFinding(
					title,
					severity,
					confidence,
					priority,
					affectedClasses,
					evidence,
					"Current design is acceptable for a small CRUD application. Future business logic may become harder to centralize if it grows across controllers.",
					"Introduce application services only if transactional, reusable, or shared business logic emerges."
			);
		}

		return new MergedFinding(
				title,
				severity,
				confidence,
				priority,
				affectedClasses,
				evidence,
				mergeText(findings.stream().map(EvidenceBasedFinding::impact).toList()),
				mergeText(findings.stream().map(EvidenceBasedFinding::recommendation).toList())
		);
	}

	private String fingerprint(EvidenceBasedFinding finding) {
		if (isControllerRepositoryAccess(finding)) {
			return "CONTROLLER_REPOSITORY_ACCESS_PATTERN";
		}
		return normalizedTitle(finding.title()) + ":" + String.join(",", finding.affectedClasses().stream().sorted().toList());
	}

	private boolean isControllerRepositoryAccess(EvidenceBasedFinding finding) {
		String title = normalizedTitle(finding.title());
		return title.contains("controller") && title.contains("repository")
				|| title.contains("missing_service")
				|| title.contains("service_abstraction");
	}

	private String mergedTitle(EvidenceBasedFinding finding) {
		if (isControllerRepositoryAccess(finding)) {
			return "Controller-to-Repository Access Pattern";
		}
		return finding.title();
	}

	private int mergedPriority(List<EvidenceBasedFinding> findings, int affectedClassCount) {
		int maxPriority = findings.stream()
				.mapToInt(finding -> finding.priority().score())
				.max()
				.orElse(0);
		int rootCauseSpreadBoost = Math.min(20, Math.max(0, affectedClassCount - 1) * 2);
		return Math.min(100, maxPriority + rootCauseSpreadBoost);
	}

	private FindingSeverity strongestSeverity(List<EvidenceBasedFinding> findings) {
		return findings.stream()
				.map(EvidenceBasedFinding::severity)
				.min(Comparator.comparingInt(Enum::ordinal))
				.orElse(FindingSeverity.INFO);
	}

	private FindingConfidence strongestConfidence(List<EvidenceBasedFinding> findings) {
		return findings.stream()
				.map(EvidenceBasedFinding::confidence)
				.min(Comparator.comparingInt(Enum::ordinal))
				.orElse(FindingConfidence.POSSIBLE);
	}

	private String mergeText(List<String> values) {
		return String.join(" ", values.stream()
				.filter(value -> value != null && !value.isBlank())
				.distinct()
				.toList());
	}

	private String normalizedTitle(String title) {
		return title == null ? "" : title.toLowerCase().replaceAll("[^a-z0-9]+", "_");
	}
}
