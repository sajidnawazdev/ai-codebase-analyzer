package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ViolationDetectorService {

	private static final int GOD_CLASS_DEPENDENCY_THRESHOLD = 5;
	private static final int GOD_CLASS_METHOD_THRESHOLD = 10;
	private static final int FAT_CONTROLLER_ENDPOINT_THRESHOLD = 6;

	public List<String> detect(List<ClassAnalysis> classes) {
		Map<String, ClassAnalysis> classByName = classes.stream()
				.collect(Collectors.toMap(ClassAnalysis::className, c -> c, (a, b) -> a));

		List<String> violations = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			for (String dependency : clazz.dependencies()) {
				ClassAnalysis target = classByName.get(dependency);
				if (target == null) {
					continue;
				}

				String violation = checkViolation(clazz, target);
				if (violation != null) {
					violations.add(violation);
				}
			}
		}

		return violations;
	}

	private String checkViolation(ClassAnalysis source, ClassAnalysis target) {
		ComponentType sourceType = source.componentType();
		ComponentType targetType = target.componentType();

		if (isController(sourceType) && targetType == ComponentType.REPOSITORY) {
			return formatViolation(source, target, "Controller bypasses service layer");
		}

		if (sourceType == ComponentType.SERVICE && isController(targetType)) {
			return formatViolation(source, target, "Service has reverse dependency on controller");
		}

		if (sourceType == ComponentType.CONFIGURATION && isController(targetType)) {
			return formatViolation(source, target, "Configuration should not depend on controller");
		}

		return null;
	}

	public List<String> detectCircularDependencies(List<ClassAnalysis> classes) {
		Map<String, ClassAnalysis> classByName = classes.stream()
				.collect(Collectors.toMap(ClassAnalysis::className, c -> c, (a, b) -> a));

		Set<String> reported = new HashSet<>();
		List<String> cycles = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			for (String dependency : clazz.dependencies()) {
				ClassAnalysis target = classByName.get(dependency);
				if (target == null) {
					continue;
				}

				if (target.dependencies().contains(clazz.className())) {
					String cycleKey = clazz.className().compareTo(target.className()) < 0
							? clazz.className() + "↔" + target.className()
							: target.className() + "↔" + clazz.className();

					if (reported.add(cycleKey)) {
						cycles.add("Circular dependency: %s → %s → %s".formatted(
								clazz.className(), target.className(), clazz.className()));
					}
				}
			}
		}

		return cycles;
	}

	public List<String> detectGodClasses(List<ClassAnalysis> classes) {
		List<String> godClasses = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			int dependencyCount = clazz.dependencies().size();
			int methodCount = clazz.methods().size();

			List<String> reasons = new ArrayList<>();

			if (dependencyCount > GOD_CLASS_DEPENDENCY_THRESHOLD) {
				reasons.add("%d dependencies (threshold: %d)".formatted(dependencyCount, GOD_CLASS_DEPENDENCY_THRESHOLD));
			}
			if (methodCount > GOD_CLASS_METHOD_THRESHOLD) {
				reasons.add("%d methods (threshold: %d)".formatted(methodCount, GOD_CLASS_METHOD_THRESHOLD));
			}

			if (!reasons.isEmpty()) {
				godClasses.add("God class: %s (%s) — %s".formatted(
						clazz.className(), clazz.componentType(), String.join(", ", reasons)));
			}
		}

		return godClasses;
	}

	public List<String> detectEmptyControllers(List<ClassAnalysis> classes, List<EndpointAnalysis> endpoints) {
		Set<String> controllersWithEndpoints = endpoints.stream()
				.map(EndpointAnalysis::controllerName)
				.collect(Collectors.toSet());

		List<String> emptyControllers = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			if (isController(clazz.componentType()) && !controllersWithEndpoints.contains(clazz.className())) {
				emptyControllers.add("Empty controller: %s (%s) — has no mapped endpoints".formatted(
						clazz.className(), clazz.packageName()));
			}
		}

		return emptyControllers;
	}

	public List<String> detectOrphanServices(List<ClassAnalysis> classes) {
		Set<String> allDependencyNames = classes.stream()
				.flatMap(clazz -> clazz.dependencies().stream())
				.collect(Collectors.toSet());

		List<String> orphans = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			if (clazz.componentType() == ComponentType.SERVICE && !allDependencyNames.contains(clazz.className())) {
				orphans.add("Orphan service: %s (%s) — no other class depends on it".formatted(
						clazz.className(), clazz.packageName()));
			}
		}

		return orphans;
	}

	public List<String> detectFatControllers(List<ClassAnalysis> classes, List<EndpointAnalysis> endpoints) {
		Map<String, Long> endpointCountByController = endpoints.stream()
				.collect(Collectors.groupingBy(EndpointAnalysis::controllerName, Collectors.counting()));

		List<String> fatControllers = new ArrayList<>();

		for (ClassAnalysis clazz : classes) {
			if (!isController(clazz.componentType())) {
				continue;
			}

			long endpointCount = endpointCountByController.getOrDefault(clazz.className(), 0L);
			if (endpointCount > FAT_CONTROLLER_ENDPOINT_THRESHOLD) {
				fatControllers.add("Fat controller: %s — %d endpoints (threshold: %d)".formatted(
						clazz.className(), endpointCount, FAT_CONTROLLER_ENDPOINT_THRESHOLD));
			}
		}

		return fatControllers;
	}

	private boolean isController(ComponentType type) {
		return type == ComponentType.REST_CONTROLLER || type == ComponentType.CONTROLLER;
	}

	private String formatViolation(ClassAnalysis source, ClassAnalysis target, String reason) {
		return "%s (%s) → %s (%s): %s".formatted(
				source.className(), source.componentType(),
				target.className(), target.componentType(),
				reason
		);
	}
}
