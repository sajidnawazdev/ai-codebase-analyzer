package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitecturalHotspot;
import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ArchitectureScoreGuidance;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArchitectureScoreAlignmentService {

	public ArchitectureScoreGuidance align(List<ArchitectureObservation> observations,
										   List<ArchitecturalHotspot> hotspots,
										   List<ClassAnalysis> classes) {
		Map<String, Integer> minimumScores = new LinkedHashMap<>();
		List<String> rulesApplied = new ArrayList<>();

		boolean noCircularDependencies = observations.stream()
				.noneMatch(observation -> observation.type().equals("CIRCULAR_DEPENDENCY"));
		boolean noSignificantHotspots = hotspots.stream()
				.noneMatch(hotspot -> hotspot.riskScore() >= 7);
		int maxCouplingScore = classes.stream()
				.mapToInt(ClassAnalysis::couplingScore)
				.max()
				.orElse(0);

		if (noCircularDependencies && noSignificantHotspots && maxCouplingScore <= 3) {
			putMinimum(minimumScores, "dependencies", 7);
			rulesApplied.add("No circular dependencies or significant architectural hotspots were detected, and max coupling <= 3: dependencies score must be >= 7.");
		}

		if (hasOnlyLowRiskControllerRepositoryAccess(observations)) {
			putMinimum(minimumScores, "layering", 7);
			rulesApplied.add("Direct repository access is only low-severity possible controller access: layering score must be >= 7.");
		}

		if (allConcernsArePossibleOrLowSeverity(observations)) {
			for (String category : List.of("layering", "modularity", "dependencies", "maintainability", "scalability")) {
				putMinimum(minimumScores, category, 6);
			}
			rulesApplied.add("All concerns are POSSIBLE or LOW/INFO severity: no category score should be below 6.");
		}

		return new ArchitectureScoreGuidance(
				minimumScores,
				rulesApplied,
				"The architecture demonstrates a simple, reasonable structure for a small CRUD-oriented Spring Boot application, with low-severity risks that should be monitored if the project grows."
		);
	}

	private boolean hasOnlyLowRiskControllerRepositoryAccess(List<ArchitectureObservation> observations) {
		List<ArchitectureObservation> layeringObservations = observations.stream()
				.filter(observation -> observation.type().equals("LAYERING"))
				.toList();
		if (layeringObservations.isEmpty()) {
			return false;
		}
		return layeringObservations.stream()
				.allMatch(observation -> observation.findingConfidence() == FindingConfidence.POSSIBLE
						&& (observation.severity() == FindingSeverity.LOW || observation.severity() == FindingSeverity.INFO));
	}

	private boolean allConcernsArePossibleOrLowSeverity(List<ArchitectureObservation> observations) {
		if (observations.isEmpty()) {
			return true;
		}
		return observations.stream()
				.allMatch(observation -> observation.findingConfidence() == FindingConfidence.POSSIBLE
						|| observation.severity() == FindingSeverity.LOW
						|| observation.severity() == FindingSeverity.INFO);
	}

	private void putMinimum(Map<String, Integer> minimumScores, String category, int score) {
		minimumScores.merge(category, score, Math::max);
	}
}
