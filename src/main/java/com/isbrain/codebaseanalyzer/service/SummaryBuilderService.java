package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.AnalysisSummary;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.PackageAnalysis;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SummaryBuilderService {

	public AnalysisSummary buildSummary(List<ClassAnalysis> classes) {
		return new AnalysisSummary(
				countByComponentType(classes, ComponentType.REST_CONTROLLER) + countByComponentType(classes, ComponentType.CONTROLLER),
				countByComponentType(classes, ComponentType.SERVICE),
				countByComponentType(classes, ComponentType.REPOSITORY),
				countByComponentType(classes, ComponentType.COMPONENT),
				countByComponentType(classes, ComponentType.CONFIGURATION),
				countByComponentType(classes, ComponentType.ENTITY),
				countByComponentType(classes, ComponentType.CONTROLLER_ADVICE),
				countByComponentType(classes, ComponentType.REST_CONTROLLER_ADVICE)
		);
	}

	public List<String> buildRelationships(List<ClassAnalysis> classes) {
		return classes.stream()
				.flatMap(classAnalysis -> classAnalysis.dependencies()
						.stream()
						.map(dependency -> classAnalysis.className() + " -> " + dependency))
				.toList();
	}

	public List<PackageAnalysis> buildPackages(List<ClassAnalysis> classes) {
		return classes.stream()
				.collect(Collectors.groupingBy(ClassAnalysis::packageName))
				.entrySet()
				.stream()
				.map(entry -> new PackageAnalysis(
						entry.getKey(),
						entry.getValue().size(),
						buildComponentTypeDistribution(entry.getValue())
				))
				.toList();
	}

	private long countByComponentType(List<ClassAnalysis> classes, ComponentType componentType) {
		return classes.stream()
				.filter(classAnalysis -> classAnalysis.componentType() == componentType)
				.count();
	}

	private Map<ComponentType, Long> buildComponentTypeDistribution(List<ClassAnalysis> classes) {
		return classes.stream()
				.collect(Collectors.groupingBy(ClassAnalysis::componentType, Collectors.counting()));
	}
}
