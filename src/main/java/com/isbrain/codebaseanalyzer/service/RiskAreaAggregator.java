package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureRiskScore;
import com.isbrain.codebaseanalyzer.model.ArchitectureStyle;
import com.isbrain.codebaseanalyzer.model.MergedFinding;
import com.isbrain.codebaseanalyzer.model.RiskArea;
import com.isbrain.codebaseanalyzer.model.RiskLevel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskAreaAggregator {

	public List<RiskArea> aggregate(
			List<MergedFinding> findings,
			ArchitectureRiskScore riskScore,
			ArchitectureStyle architectureStyle
	) {
		return List.of(
				riskArea("Layering", levelFor(riskScore.layeringRisk()),
						filter(findings, "layer", "repository", "service abstraction"),
						layeringSummary(architectureStyle)),
				riskArea("Coupling", levelFor(riskScore.couplingRisk()),
						filter(findings, "coupling", "dependency", "orchestration"),
						"No dominant coupling risk was detected beyond the listed findings."),
				riskArea("Modularity", levelFor(riskScore.modularityRisk()),
						filter(findings, "module", "responsibility", "controller"),
						"Modularity risk is based on responsibility concentration and package-level structure."),
				riskArea("Maintainability", levelFor(riskScore.maintainabilityRisk()),
						filter(findings, "complexity", "god", "maintain", "entity"),
						"Maintainability risk is based on class size, entity complexity, and responsibility growth."),
				riskArea("Scalability", levelFor(riskScore.scalabilityRisk()),
						filter(findings, "pagination", "scalability", "fan-out"),
						"Scalability risk is based on growth-sensitive patterns such as unbounded list endpoints and fan-out."),
				riskArea("Performance", RiskLevel.LOW,
						filter(findings, "performance", "inefficient"),
						"No dedicated performance analyzer is enabled yet; only architecture-derived signals are considered."),
				riskArea("Spring Practices", springPracticeLevel(findings),
						filter(findings, "pagination", "transactional", "repository"),
						"Spring practice risk reflects framework-specific usage patterns detected statically.")
		);
	}

	private RiskArea riskArea(String name, RiskLevel level, List<MergedFinding> findings, String summary) {
		return new RiskArea(name, level, findings, summary);
	}

	private RiskLevel levelFor(int score) {
		if (score >= 75) {
			return RiskLevel.CRITICAL;
		}
		if (score >= 50) {
			return RiskLevel.HIGH;
		}
		if (score >= 25) {
			return RiskLevel.MEDIUM;
		}
		return RiskLevel.LOW;
	}

	private RiskLevel springPracticeLevel(List<MergedFinding> findings) {
		int maxPriority = filter(findings, "pagination", "transactional", "repository").stream()
				.mapToInt(MergedFinding::priority)
				.max()
				.orElse(0);
		if (maxPriority >= 75) {
			return RiskLevel.HIGH;
		}
		if (maxPriority >= 25) {
			return RiskLevel.MEDIUM;
		}
		return RiskLevel.LOW;
	}

	private List<MergedFinding> filter(List<MergedFinding> findings, String... tokens) {
		return findings.stream()
				.filter(finding -> matches(finding, tokens))
				.toList();
	}

	private boolean matches(MergedFinding finding, String[] tokens) {
		String text = (finding.title() + " " + finding.impact() + " " + finding.recommendation()).toLowerCase();
		for (String token : tokens) {
			if (text.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private String layeringSummary(ArchitectureStyle architectureStyle) {
		if (architectureStyle == ArchitectureStyle.SIMPLE_CRUD) {
			return "The project intentionally follows a simple CRUD architecture. Current layering choices are appropriate unless business logic becomes transactional, reusable, or shared.";
		}
		return "Layering risk reflects whether dependencies preserve expected architectural boundaries.";
	}
}
