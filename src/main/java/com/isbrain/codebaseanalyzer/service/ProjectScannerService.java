package com.isbrain.codebaseanalyzer.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ProjectAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectScannerService {

	private final ClassAnalyserService classAnalyserService;
	private final SummaryBuilderService summaryBuilderService;
	private final ViolationDetectorService violationDetectorService;
	private final MermaidGeneratorService mermaidGeneratorService;

	public List<Path> scanJavaFiles(String projectPath) {
		try (var paths = Files.walk(Path.of(projectPath))) {
			return paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.filter(path -> !path.toString().contains("src" + path.getFileSystem().getSeparator() + "test"))
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public List<ClassAnalysis> analyseJavaFiles(String projectPath) {
		return scanJavaFiles(projectPath)
				.stream()
				.flatMap(path -> classAnalyserService.analyseJavaFile(path).classes().stream())
				.toList();
	}

	public ProjectAnalysisResult analyseProject(String projectPath) {
		var fileAnalyses = scanJavaFiles(projectPath)
				.stream()
				.map(classAnalyserService::analyseJavaFile)
				.toList();
		var parsedClasses = fileAnalyses.stream()
				.flatMap(fileAnalysis -> fileAnalysis.classes().stream())
				.toList();
		var endpoints = fileAnalyses.stream()
				.flatMap(fileAnalysis -> fileAnalysis.endpoints().stream())
				.toList();

		var rawClasses = classAnalyserService.resolveHierarchyTypes(parsedClasses);

		Set<String> projectClassNames = rawClasses.stream()
				.map(ClassAnalysis::className)
				.collect(Collectors.toSet());

		var filteredClasses = rawClasses.stream()
				.map(clazz -> new ClassAnalysis(
						clazz.className(),
						clazz.packageName(),
						clazz.annotations(),
						clazz.methods(),
						clazz.allDependencies().stream()
								.filter(projectClassNames::contains)
								.toList(),
						clazz.allDependencies(),
						clazz.componentType(),
						clazz.superClass(),
						clazz.implementedInterfaces(),
						0
				))
				.toList();

		Map<String, Integer> incomingCounts = new HashMap<>();
		for (ClassAnalysis clazz : filteredClasses) {
			for (String dep : clazz.dependencies()) {
				incomingCounts.merge(dep, 1, Integer::sum);
			}
		}

		var classes = filteredClasses.stream()
				.map(clazz -> {
					int outgoing = clazz.dependencies().size();
					int incoming = incomingCounts.getOrDefault(clazz.className(), 0);
					return new ClassAnalysis(
							clazz.className(),
							clazz.packageName(),
							clazz.annotations(),
							clazz.methods(),
							clazz.dependencies(),
							clazz.allDependencies(),
							clazz.componentType(),
							clazz.superClass(),
							clazz.implementedInterfaces(),
							outgoing + incoming
					);
				})
				.toList();

		List<String> violations = violationDetectorService.detect(classes);
		List<String> circularDependencies = violationDetectorService.detectCircularDependencies(classes);
		List<String> godClasses = violationDetectorService.detectGodClasses(classes);
		List<String> emptyControllers = violationDetectorService.detectEmptyControllers(classes, endpoints);
		List<String> orphanServices = violationDetectorService.detectOrphanServices(classes);
		List<String> fatControllers = violationDetectorService.detectFatControllers(classes, endpoints);

		List<String> couplingRanking = classes.stream()
				.filter(clazz -> clazz.couplingScore() > 0)
				.sorted((a, b) -> Integer.compare(b.couplingScore(), a.couplingScore()))
				.limit(3)
				.map(clazz -> {
					int outgoing = clazz.dependencies().size();
					int incoming = clazz.couplingScore() - outgoing;
					return "%s (%s) — coupling score: %d (outgoing: %d, incoming: %d)".formatted(
							clazz.className(), clazz.componentType(),
							clazz.couplingScore(), outgoing, incoming);
				})
				.toList();

		return new ProjectAnalysisResult(
				summaryBuilderService.buildSummary(classes),
				summaryBuilderService.buildRelationships(classes),
				endpoints,
				summaryBuilderService.buildPackages(classes),
				classes,
				violations.isEmpty() ? List.of("No layer violations detected") : violations,
				circularDependencies,
				godClasses,
				emptyControllers,
				orphanServices,
				fatControllers,
				couplingRanking,
				mermaidGeneratorService.generateDiagram(classes)
		);
	}
}
