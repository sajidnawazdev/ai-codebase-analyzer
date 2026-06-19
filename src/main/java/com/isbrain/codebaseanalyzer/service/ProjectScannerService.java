package com.isbrain.codebaseanalyzer.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ClassMetrics;
import com.isbrain.codebaseanalyzer.model.ProjectAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectScannerService {

	private final ClassAnalyserService classAnalyserService;
	private final SummaryBuilderService summaryBuilderService;
	private final ArchitectureObservationDetectorService observationDetectorService;
	private final MermaidGeneratorService mermaidGeneratorService;
	private final ArchitectureHotspotAnalyzer architectureHotspotAnalyzer;
	private final ArchitectureRiskScorer architectureRiskScorer;
	private final ArchitectureScoreAlignmentService architectureScoreAlignmentService;
	private final EvidenceFindingBuilder evidenceFindingBuilder;
	private final DependencyDirectionAnalyzer dependencyDirectionAnalyzer;
	private final SpringSpecificAnalyzer springSpecificAnalyzer;
	private final FindingMergeService findingMergeService;
	private final ArchitectureStyleClassifier architectureStyleClassifier;
	private final ArchitectureMaturityClassifier architectureMaturityClassifier;
	private final RiskAreaAggregator riskAreaAggregator;

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
		var classMetrics = fileAnalyses.stream()
				.flatMap(fileAnalysis -> fileAnalysis.metrics().stream())
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

		List<ArchitectureObservation> observations = new ArrayList<>();
		observations.addAll(observationDetectorService.detect(classes));
		observations.addAll(observationDetectorService.detectCircularDependencies(classes));
		observations.addAll(observationDetectorService.detectGodClasses(classes, classMetrics));
		observations.addAll(observationDetectorService.detectEmptyControllers(classes, endpoints));
		observations.addAll(observationDetectorService.detectOrphanServices(classes));
		observations.addAll(observationDetectorService.detectFatControllers(classes, endpoints, classMetrics));
		observations.addAll(springSpecificAnalyzer.detectListEndpointsWithoutPagination(endpoints));
		observations.addAll(springSpecificAnalyzer.detectBestPracticeIssues(
				fileAnalyses.stream()
						.map(ClassAnalyserService.JavaFileAnalysis::path)
						.toList()
		));

		var summary = summaryBuilderService.buildSummary(classes);
		var packages = summaryBuilderService.buildPackages(classes);
		var architectureStyleAssessment = architectureStyleClassifier.assess(summary, classes, endpoints, packages);
		var architectureStyle = architectureStyleAssessment.primary();
		var architectureMaturity = architectureMaturityClassifier.classify(classes, packages);
		var hotspots = architectureHotspotAnalyzer.analyze(classMetrics);
		var boundaryFindings = dependencyDirectionAnalyzer.analyze(classes);
		var riskScore = architectureRiskScorer.score(observations, classMetrics, classes);
		var scoreGuidance = architectureScoreAlignmentService.align(observations, hotspots, classes);
		var evidenceBasedFindings = evidenceFindingBuilder.build(observations, architectureMaturity, architectureStyle);
		var mergedFindings = findingMergeService.merge(evidenceBasedFindings, architectureMaturity, architectureStyle);
		var riskAreas = riskAreaAggregator.aggregate(mergedFindings, riskScore, architectureStyle, observations, hotspots);

		List<String> couplingRanking = classes.stream()
				.filter(clazz -> clazz.couplingScore() > 0)
				.sorted((a, b) -> Integer.compare(b.couplingScore(), a.couplingScore()))
				.limit(3)
				.map(clazz -> {
					int outgoing = clazz.dependencies().size();
					int incoming = clazz.couplingScore() - outgoing;
					return "%s (%s) - coupling score: %d (outgoing: %d, incoming: %d)".formatted(
							clazz.className(), clazz.componentType(),
							clazz.couplingScore(), outgoing, incoming);
				})
				.toList();

		return new ProjectAnalysisResult(
				summary,
				summaryBuilderService.buildRelationships(classes),
				endpoints,
				packages,
				classes,
				classMetrics,
				hotspots,
				boundaryFindings,
				observations,
				architectureStyle,
				architectureStyleAssessment,
				riskScore,
				scoreGuidance,
				evidenceBasedFindings,
				mergedFindings,
				riskAreas,
				couplingRanking,
				mermaidGeneratorService.generateDiagram(classes),
				architectureMaturity
		);
	}
}
