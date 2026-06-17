package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureBoundaryFinding;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import com.isbrain.codebaseanalyzer.model.Layer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DependencyDirectionAnalyzer {

	private static final Set<LayerDependency> ALLOWED_DEPENDENCIES = Set.of(
			new LayerDependency(Layer.CONTROLLER, Layer.SERVICE),
			new LayerDependency(Layer.CONTROLLER, Layer.REPOSITORY),
			new LayerDependency(Layer.SERVICE, Layer.REPOSITORY),
			new LayerDependency(Layer.SERVICE, Layer.SERVICE),
			new LayerDependency(Layer.REPOSITORY, Layer.ENTITY)
	);

	private static final Set<LayerDependency> FORBIDDEN_DEPENDENCIES = Set.of(
			new LayerDependency(Layer.ENTITY, Layer.CONTROLLER),
			new LayerDependency(Layer.ENTITY, Layer.SERVICE),
			new LayerDependency(Layer.REPOSITORY, Layer.CONTROLLER),
			new LayerDependency(Layer.REPOSITORY, Layer.SERVICE),
			new LayerDependency(Layer.CONFIGURATION, Layer.CONTROLLER)
	);

	public List<ArchitectureBoundaryFinding> analyze(List<ClassAnalysis> classes) {
		Map<String, ClassAnalysis> classByName = classes.stream()
				.collect(Collectors.toMap(ClassAnalysis::className, c -> c, (a, b) -> a));

		List<ArchitectureBoundaryFinding> findings = new ArrayList<>();
		for (ClassAnalysis source : classes) {
			Layer sourceLayer = toLayer(source.componentType());
			for (String dependency : source.dependencies()) {
				ClassAnalysis target = classByName.get(dependency);
				if (target == null) {
					continue;
				}

				Layer targetLayer = toLayer(target.componentType());
				LayerDependency direction = new LayerDependency(sourceLayer, targetLayer);
				if (isAllowed(direction) || !isForbidden(direction)) {
					continue;
				}

				findings.add(new ArchitectureBoundaryFinding(
						source.className(),
						"%s_DEPENDS_ON_%s".formatted(sourceLayer, targetLayer),
						severityFor(direction)
				));
			}
		}
		return findings;
	}

	private boolean isAllowed(LayerDependency direction) {
		return ALLOWED_DEPENDENCIES.contains(direction);
	}

	private boolean isForbidden(LayerDependency direction) {
		return FORBIDDEN_DEPENDENCIES.contains(direction);
	}

	private FindingSeverity severityFor(LayerDependency direction) {
		if (direction.source() == Layer.ENTITY) {
			return FindingSeverity.HIGH;
		}
		if (direction.source() == Layer.REPOSITORY) {
			return FindingSeverity.HIGH;
		}
		return FindingSeverity.MEDIUM;
	}

	private Layer toLayer(ComponentType componentType) {
		return switch (componentType) {
			case REST_CONTROLLER, CONTROLLER -> Layer.CONTROLLER;
			case SERVICE -> Layer.SERVICE;
			case REPOSITORY -> Layer.REPOSITORY;
			case ENTITY -> Layer.ENTITY;
			case CONFIGURATION -> Layer.CONFIGURATION;
			case COMPONENT -> Layer.COMPONENT;
			default -> Layer.UNKNOWN;
		};
	}

	private record LayerDependency(Layer source, Layer target) {
	}
}
