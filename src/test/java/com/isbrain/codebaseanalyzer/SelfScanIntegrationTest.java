package com.isbrain.codebaseanalyzer;

import com.isbrain.codebaseanalyzer.model.ProjectAnalysisResult;
import com.isbrain.codebaseanalyzer.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SelfScanIntegrationTest {

	private ProjectScannerService scannerService;

	@BeforeEach
	void setUp() {
		var endpointExtractor = new EndpointExtractorService();
		var classAnalyser = new ClassAnalyserService(endpointExtractor);
		var summaryBuilder = new SummaryBuilderService();
		var observationDetector = new ArchitectureObservationDetectorService();
		var mermaidGenerator = new MermaidGeneratorService();
		var hotspotAnalyzer = new ArchitectureHotspotAnalyzer();
		var riskScorer = new ArchitectureRiskScorer();
		var scoreAlignmentService = new ArchitectureScoreAlignmentService();
		var evidenceFindingBuilder = new EvidenceFindingBuilder();
		var dependencyDirectionAnalyzer = new DependencyDirectionAnalyzer();
		var springSpecificAnalyzer = new SpringSpecificAnalyzer();
		var findingMergeService = new FindingMergeService();
		var architectureStyleClassifier = new ArchitectureStyleClassifier();
		var architectureMaturityClassifier = new ArchitectureMaturityClassifier();
		var riskAreaAggregator = new RiskAreaAggregator();

		scannerService = new ProjectScannerService(
				classAnalyser, summaryBuilder, observationDetector, mermaidGenerator, hotspotAnalyzer, riskScorer,
				scoreAlignmentService, evidenceFindingBuilder, dependencyDirectionAnalyzer, springSpecificAnalyzer,
				findingMergeService, architectureStyleClassifier, architectureMaturityClassifier, riskAreaAggregator);
	}

	@Test
	void scanOwnProjectReturnsValidResult() {
		ProjectAnalysisResult result = scannerService.analyseProject("src/main/java");

		assertNotNull(result);
		assertNotNull(result.summary());
		assertNotNull(result.relationships());
		assertNotNull(result.endpoints());
		assertNotNull(result.packages());
		assertNotNull(result.classes());
		assertNotNull(result.observations());
		assertNotNull(result.boundaryFindings());
		assertNotNull(result.riskScore());
		assertNotNull(result.scoreGuidance());
		assertNotNull(result.evidenceBasedFindings());
		assertNotNull(result.mergedFindings());
		assertNotNull(result.architectureStyle());
		assertNotNull(result.architectureStyleAssessment());
		assertNotNull(result.architectureMaturity());
		assertNotNull(result.riskAreas());
		assertNotNull(result.mermaidDiagram());
	}

	@Test
	void scanFindsExpectedControllers() {
		ProjectAnalysisResult result = scannerService.analyseProject("src/main/java");

		assertTrue(result.summary().controllers() >= 1,
				"Should find at least 1 controller (AnalysisController)");
	}

	@Test
	void scanFindsExpectedServices() {
		ProjectAnalysisResult result = scannerService.analyseProject("src/main/java");

		assertTrue(result.summary().services() >= 5,
				"Should find at least 5 services");
	}

	@Test
	void scanFindsEndpoints() {
		ProjectAnalysisResult result = scannerService.analyseProject("src/main/java");

		assertTrue(result.endpoints().size() >= 2,
				"Should find at least 2 endpoints (/analyse and /analyse/ai)");
	}

	@Test
	void scanFindsRelationships() {
		ProjectAnalysisResult result = scannerService.analyseProject("src/main/java");

		assertFalse(result.relationships().isEmpty(),
				"Should find class relationships");
	}

	@Test
	void scanFindsPackages() {
		ProjectAnalysisResult result = scannerService.analyseProject("src/main/java");

		assertTrue(result.packages().size() >= 3,
				"Should find at least 3 packages (controller, service, model)");
	}

	@Test
	void scanGeneratesMermaidDiagram() {
		ProjectAnalysisResult result = scannerService.analyseProject("src/main/java");

		assertTrue(result.mermaidDiagram().startsWith("graph TD"),
				"Mermaid diagram should start with graph TD");
		assertTrue(result.mermaidDiagram().contains("-->"),
				"Mermaid diagram should contain edges");
	}

	@Test
	void scanProducesCouplingRanking() {
		ProjectAnalysisResult result = scannerService.analyseProject("src/main/java");

		assertFalse(result.couplingRanking().isEmpty(),
				"Should produce a coupling ranking");
		assertTrue(result.couplingRanking().size() <= 3,
				"Coupling ranking should have at most 3 entries");
	}

	@Test
	void scanDetectsConfigurationClass() {
		ProjectAnalysisResult result = scannerService.analyseProject("src/main/java");

		assertTrue(result.summary().configurations() >= 1,
				"Should find at least 1 configuration class (AiConfig)");
	}
}
