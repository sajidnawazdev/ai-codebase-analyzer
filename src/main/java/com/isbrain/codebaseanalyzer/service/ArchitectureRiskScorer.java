package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ArchitectureRiskScore;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ClassMetrics;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ArchitectureRiskScorer {

	private static final int MAX_RISK = 100;

	public ArchitectureRiskScore score(List<ArchitectureObservation> observations,
									   List<ClassMetrics> classMetrics,
									   List<ClassAnalysis> classes) {
		return new ArchitectureRiskScore(
				scoreLayeringRisk(observations),
				scoreCouplingRisk(classMetrics),
				scoreModularityRisk(observations, classes),
				scoreMaintainabilityRisk(classMetrics),
				scoreScalabilityRisk(classMetrics, classes)
		);
	}

	private int scoreLayeringRisk(List<ArchitectureObservation> observations) {
		int risk = observations.stream()
				.filter(observation -> observation.type().equals("LAYERING"))
				.mapToInt(observation -> 5)
				.sum();
		return cap(risk);
	}

	private int scoreCouplingRisk(List<ClassMetrics> classMetrics) {
		int risk = classMetrics.stream()
				.mapToInt(metrics -> {
					if (metrics.dependencyCount() >= 10) {
						return 10;
					}
					if (metrics.dependencyCount() >= 6) {
						return 5;
					}
					return 0;
				})
				.sum();
		return cap(risk);
	}

	private int scoreModularityRisk(List<ArchitectureObservation> observations, List<ClassAnalysis> classes) {
		int classCycleRisk = observations.stream()
				.filter(observation -> observation.type().equals("CIRCULAR_DEPENDENCY"))
				.mapToInt(observation -> 25)
				.sum();

		return cap(classCycleRisk + countDirectPackageCycles(classes) * 20);
	}

	private int scoreMaintainabilityRisk(List<ClassMetrics> classMetrics) {
		int risk = classMetrics.stream()
				.mapToInt(metrics -> lineCountRisk(metrics) + publicApiRisk(metrics))
				.sum();
		return cap(risk);
	}

	private int scoreScalabilityRisk(List<ClassMetrics> classMetrics, List<ClassAnalysis> classes) {
		int highCouplingRisk = classMetrics.stream()
				.filter(metrics -> metrics.dependencyCount() > 8)
				.mapToInt(metrics -> 5)
				.sum();

		int packageFanOutRisk = packageDependencies(classes).values()
				.stream()
				.filter(dependencies -> dependencies.size() > 5)
				.mapToInt(dependencies -> 5)
				.sum();

		return cap(highCouplingRisk + packageFanOutRisk);
	}

	private int lineCountRisk(ClassMetrics metrics) {
		if (metrics.lineCount() >= 1000) {
			return 10;
		}
		if (metrics.lineCount() >= 500) {
			return 5;
		}
		return 0;
	}

	private int publicApiRisk(ClassMetrics metrics) {
		if (metrics.publicMethodCount() >= 25) {
			return 10;
		}
		if (metrics.publicMethodCount() >= 15) {
			return 5;
		}
		return 0;
	}

	private int countDirectPackageCycles(List<ClassAnalysis> classes) {
		Map<String, Set<String>> packageDependencies = packageDependencies(classes);
		Set<String> reportedCycles = new HashSet<>();

		for (Map.Entry<String, Set<String>> entry : packageDependencies.entrySet()) {
			String sourcePackage = entry.getKey();
			for (String targetPackage : entry.getValue()) {
				if (!packageDependencies.getOrDefault(targetPackage, Set.of()).contains(sourcePackage)) {
					continue;
				}
				String cycleKey = sourcePackage.compareTo(targetPackage) < 0
						? sourcePackage + "<->" + targetPackage
						: targetPackage + "<->" + sourcePackage;
				reportedCycles.add(cycleKey);
			}
		}

		return reportedCycles.size();
	}

	private Map<String, Set<String>> packageDependencies(List<ClassAnalysis> classes) {
		Map<String, String> packageByClassName = classes.stream()
				.collect(Collectors.toMap(ClassAnalysis::className, ClassAnalysis::packageName, (a, b) -> a));

		Map<String, Set<String>> dependenciesByPackage = new HashMap<>();
		for (ClassAnalysis clazz : classes) {
			Set<String> dependencies = dependenciesByPackage.computeIfAbsent(clazz.packageName(), ignored -> new HashSet<>());
			for (String dependency : clazz.dependencies()) {
				String targetPackage = packageByClassName.get(dependency);
				if (targetPackage != null && !targetPackage.equals(clazz.packageName())) {
					dependencies.add(targetPackage);
				}
			}
		}
		return dependenciesByPackage;
	}

	private int cap(int risk) {
		return Math.min(MAX_RISK, Math.max(0, risk));
	}
}
