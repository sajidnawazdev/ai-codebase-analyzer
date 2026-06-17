package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ClassMetrics;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ArchitectureObservationDetectorService {

	private static final int GOD_CLASS_DEPENDENCY_THRESHOLD = 8;
	private static final int GOD_CLASS_METHOD_THRESHOLD = 25;
	private static final int GOD_CLASS_LOC_THRESHOLD = 500;
	private static final int FAT_CONTROLLER_ENDPOINT_THRESHOLD = 15;
	private static final int FAT_CONTROLLER_DEPENDENCY_THRESHOLD = 6;
	private static final int FAT_CONTROLLER_LOC_THRESHOLD = 400;
	private static final int DOMAIN_ENTITY_COMPLEXITY_METHOD_THRESHOLD = 13;

	public List<ArchitectureObservation> detect(List<ClassAnalysis> classes) {
		Map<String, ClassAnalysis> classByName = classes.stream()
				.collect(Collectors.toMap(ClassAnalysis::className, c -> c, (a, b) -> a));

		List<ArchitectureObservation> observations = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			for (String dependency : clazz.dependencies()) {
				ClassAnalysis target = classByName.get(dependency);
				if (target == null) {
					continue;
				}

				ArchitectureObservation observation = checkLayering(clazz, target);
				if (observation != null) {
					observations.add(observation);
				}
			}
		}

		return observations;
	}

	private ArchitectureObservation checkLayering(ClassAnalysis source, ClassAnalysis target) {
		ComponentType sourceType = source.componentType();
		ComponentType targetType = target.componentType();

		if (isController(sourceType) && targetType == ComponentType.REPOSITORY) {
			return new ArchitectureObservation(
					"LAYERING",
					source.className(),
					"%s accesses %s directly. Determine whether this is an intentional architectural choice or a layering concern.".formatted(
							source.className(), target.className()),
					60,
					true,
					FindingConfidence.POSSIBLE,
					FindingSeverity.LOW
			);
		}

		if (sourceType == ComponentType.SERVICE && isController(targetType)) {
			return new ArchitectureObservation(
					"LAYERING",
					source.className(),
					"%s depends on %s. Services depending on controllers is a reverse dependency that typically indicates a design issue.".formatted(
							source.className(), target.className()),
					90,
					false,
					FindingConfidence.CONFIRMED,
					FindingSeverity.HIGH
			);
		}

		if (sourceType == ComponentType.CONFIGURATION && isController(targetType)) {
			return new ArchitectureObservation(
					"LAYERING",
					source.className(),
					"%s depends on %s. Configuration classes depending on controllers is unusual and may indicate a design issue.".formatted(
							source.className(), target.className()),
					80,
					true,
					FindingConfidence.LIKELY,
					FindingSeverity.MEDIUM
			);
		}

		return null;
	}

	public List<ArchitectureObservation> detectCircularDependencies(List<ClassAnalysis> classes) {
		Map<String, ClassAnalysis> classByName = classes.stream()
				.collect(Collectors.toMap(ClassAnalysis::className, c -> c, (a, b) -> a));

		Set<String> reported = new HashSet<>();
		List<ArchitectureObservation> observations = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			for (String dependency : clazz.dependencies()) {
				ClassAnalysis target = classByName.get(dependency);
				if (target == null) {
					continue;
				}

				if (target.dependencies().contains(clazz.className())) {
					String cycleKey = clazz.className().compareTo(target.className()) < 0
							? clazz.className() + "<->" + target.className()
							: target.className() + "<->" + clazz.className();

					if (reported.add(cycleKey)) {
						observations.add(new ArchitectureObservation(
								"CIRCULAR_DEPENDENCY",
								clazz.className(),
								"%s and %s depend on each other. Circular dependencies can complicate testing and deployment.".formatted(
										clazz.className(), target.className()),
								85,
								false,
								FindingConfidence.CONFIRMED,
								FindingSeverity.HIGH
						));
					}
				}
			}
		}

		return observations;
	}

	public List<ArchitectureObservation> detectGodClasses(List<ClassAnalysis> classes, List<ClassMetrics> metrics) {
		Map<String, ClassMetrics> metricsByClassName = metrics.stream()
				.collect(Collectors.toMap(ClassMetrics::className, m -> m, (a, b) -> a));
		List<ArchitectureObservation> observations = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			ClassMetrics classMetrics = metricsByClassName.get(clazz.className());
			int dependencyCount = classMetrics != null ? classMetrics.dependencyCount() : clazz.dependencies().size();
			int methodCount = classMetrics != null ? classMetrics.methodCount() : clazz.methods().size();
			int lineCount = classMetrics != null ? classMetrics.lineCount() : 0;

			List<String> reasons = new ArrayList<>();

			if (dependencyCount >= GOD_CLASS_DEPENDENCY_THRESHOLD) {
				reasons.add("%d dependencies (threshold: %d)".formatted(dependencyCount, GOD_CLASS_DEPENDENCY_THRESHOLD));
			}
			if (methodCount >= GOD_CLASS_METHOD_THRESHOLD) {
				reasons.add("%d methods (threshold: %d)".formatted(methodCount, GOD_CLASS_METHOD_THRESHOLD));
			}
			if (lineCount >= GOD_CLASS_LOC_THRESHOLD) {
				reasons.add("%d LOC (threshold: %d)".formatted(lineCount, GOD_CLASS_LOC_THRESHOLD));
			}

			if (!reasons.isEmpty()) {
				observations.add(new ArchitectureObservation(
						"GOD_CLASS",
						clazz.className(),
						"%s (%s) has %s. Evaluate whether this class has too many responsibilities.".formatted(
								clazz.className(), clazz.componentType(), String.join(", ", reasons)),
						calculateGodClassConfidence(reasons.size(), lineCount, methodCount, dependencyCount),
						true,
						reasons.size() > 1 ? FindingConfidence.CONFIRMED : FindingConfidence.LIKELY,
						FindingSeverity.HIGH
				));
			} else if (clazz.componentType() == ComponentType.ENTITY && methodCount >= DOMAIN_ENTITY_COMPLEXITY_METHOD_THRESHOLD) {
				observations.add(new ArchitectureObservation(
						"DOMAIN_ENTITY_COMPLEXITY",
						clazz.className(),
						"%s has %d methods. This may reflect domain entity complexity, not a god class.".formatted(
								clazz.className(), methodCount),
						45,
						true,
						FindingConfidence.POSSIBLE,
						FindingSeverity.LOW
				));
			}
		}

		return observations;
	}

	public List<ArchitectureObservation> detectEmptyControllers(List<ClassAnalysis> classes, List<EndpointAnalysis> endpoints) {
		Set<String> controllersWithEndpoints = endpoints.stream()
				.map(EndpointAnalysis::controllerName)
				.collect(Collectors.toSet());

		List<ArchitectureObservation> observations = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			if (isController(clazz.componentType()) && !controllersWithEndpoints.contains(clazz.className())) {
				observations.add(new ArchitectureObservation(
						"EMPTY_CONTROLLER",
						clazz.className(),
						"%s has no mapped endpoints. It may be unused or under development.".formatted(
								clazz.className()),
						70,
						true,
						FindingConfidence.POSSIBLE,
						FindingSeverity.LOW
				));
			}
		}

		return observations;
	}

	public List<ArchitectureObservation> detectOrphanServices(List<ClassAnalysis> classes) {
		Set<String> allDependencyNames = classes.stream()
				.flatMap(clazz -> clazz.dependencies().stream())
				.collect(Collectors.toSet());

		List<ArchitectureObservation> observations = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			if (clazz.componentType() == ComponentType.SERVICE && !allDependencyNames.contains(clazz.className())) {
				observations.add(new ArchitectureObservation(
						"ORPHAN_SERVICE",
						clazz.className(),
						"%s is not injected into any other class. It may be unused, invoked dynamically, or under development.".formatted(
								clazz.className()),
						50,
						true,
						FindingConfidence.POSSIBLE,
						FindingSeverity.INFO
				));
			}
		}

		return observations;
	}

	public List<ArchitectureObservation> detectFatControllers(List<ClassAnalysis> classes, List<EndpointAnalysis> endpoints,
															  List<ClassMetrics> metrics) {
		Map<String, Long> endpointCountByController = endpoints.stream()
				.collect(Collectors.groupingBy(EndpointAnalysis::controllerName, Collectors.counting()));
		Map<String, ClassMetrics> metricsByClassName = metrics.stream()
				.collect(Collectors.toMap(ClassMetrics::className, m -> m, (a, b) -> a));

		List<ArchitectureObservation> observations = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			if (!isController(clazz.componentType())) {
				continue;
			}

			long endpointCount = endpointCountByController.getOrDefault(clazz.className(), 0L);
			ClassMetrics classMetrics = metricsByClassName.get(clazz.className());
			int dependencyCount = classMetrics != null ? classMetrics.dependencyCount() : clazz.dependencies().size();
			int lineCount = classMetrics != null ? classMetrics.lineCount() : 0;
			if (endpointCount >= FAT_CONTROLLER_ENDPOINT_THRESHOLD
					|| dependencyCount >= FAT_CONTROLLER_DEPENDENCY_THRESHOLD
					|| lineCount >= FAT_CONTROLLER_LOC_THRESHOLD) {
				observations.add(new ArchitectureObservation(
						"FAT_CONTROLLER",
						clazz.className(),
						"%s has %d endpoints, %d dependencies, and %d LOC. Consider whether it manages too many resources.".formatted(
								clazz.className(), endpointCount, dependencyCount, lineCount),
						65,
						true,
						FindingConfidence.POSSIBLE,
						FindingSeverity.MEDIUM
				));
			}
		}

		return observations;
	}

	private boolean isController(ComponentType type) {
		return type == ComponentType.REST_CONTROLLER || type == ComponentType.CONTROLLER;
	}

	private int calculateGodClassConfidence(int reasonCount, int lineCount, int methodCount, int dependencyCount) {
		if (reasonCount > 1) {
			return 85;
		}
		if (lineCount >= 1000 || methodCount >= 35 || dependencyCount >= 12) {
			return 80;
		}
		return 70;
	}
}
