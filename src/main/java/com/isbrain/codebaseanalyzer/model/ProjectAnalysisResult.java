package com.isbrain.codebaseanalyzer.model;

import java.util.List;

public record ProjectAnalysisResult(
		AnalysisSummary summary,
		List<String> relationships,
		List<EndpointAnalysis> endpoints,
		List<PackageAnalysis> packages,
		List<ClassAnalysis> classes,
		List<ClassMetrics> classMetrics,
		List<ArchitecturalHotspot> hotspots,
		List<ArchitectureBoundaryFinding> boundaryFindings,
		List<ArchitectureObservation> observations,
		ArchitectureStyle architectureStyle,
		ArchitectureStyleAssessment architectureStyleAssessment,
		ArchitectureRiskScore riskScore,
		ArchitectureScoreGuidance scoreGuidance,
		List<EvidenceBasedFinding> evidenceBasedFindings,
		List<MergedFinding> mergedFindings,
		List<RiskArea> riskAreas,
		List<String> couplingRanking,
		String mermaidDiagram,
		ArchitectureMaturity architectureMaturity
) {
	public ProjectAnalysisResult(
			AnalysisSummary summary,
			List<String> relationships,
			List<EndpointAnalysis> endpoints,
			List<PackageAnalysis> packages,
			List<ClassAnalysis> classes,
			List<ClassMetrics> classMetrics,
			List<ArchitecturalHotspot> hotspots,
			List<ArchitectureBoundaryFinding> boundaryFindings,
			List<ArchitectureObservation> observations,
			ArchitectureStyle architectureStyle,
			ArchitectureStyleAssessment architectureStyleAssessment,
			ArchitectureRiskScore riskScore,
			ArchitectureScoreGuidance scoreGuidance,
			List<EvidenceBasedFinding> evidenceBasedFindings,
			List<MergedFinding> mergedFindings,
			List<RiskArea> riskAreas,
			List<String> couplingRanking,
			String mermaidDiagram
	) {
		this(summary, relationships, endpoints, packages, classes, classMetrics, hotspots, boundaryFindings,
				observations, architectureStyle, architectureStyleAssessment, riskScore, scoreGuidance,
				evidenceBasedFindings, mergedFindings, riskAreas, couplingRanking, mermaidDiagram,
				ArchitectureMaturity.PROTOTYPE);
	}

	public ProjectAnalysisResult(
			AnalysisSummary summary,
			List<String> relationships,
			List<EndpointAnalysis> endpoints,
			List<PackageAnalysis> packages,
			List<ClassAnalysis> classes,
			List<ClassMetrics> classMetrics,
			List<ArchitecturalHotspot> hotspots,
			List<ArchitectureBoundaryFinding> boundaryFindings,
			List<ArchitectureObservation> observations,
			ArchitectureRiskScore riskScore,
			ArchitectureScoreGuidance scoreGuidance,
			List<EvidenceBasedFinding> evidenceBasedFindings,
			List<String> couplingRanking,
			String mermaidDiagram
	) {
		this(summary, relationships, endpoints, packages, classes, classMetrics, hotspots, boundaryFindings,
				observations, ArchitectureStyle.UNKNOWN,
				new ArchitectureStyleAssessment(ArchitectureStyle.UNKNOWN, FindingConfidence.POSSIBLE, List.of()),
				riskScore, scoreGuidance, evidenceBasedFindings, List.of(), List.of(), couplingRanking, mermaidDiagram,
				ArchitectureMaturity.PROTOTYPE);
	}
}
