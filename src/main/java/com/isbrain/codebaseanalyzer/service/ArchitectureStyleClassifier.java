package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.AnalysisSummary;
import com.isbrain.codebaseanalyzer.model.ArchitectureStyle;
import com.isbrain.codebaseanalyzer.model.ArchitectureStyleAssessment;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.PackageAnalysis;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ArchitectureStyleClassifier {

	public ArchitectureStyle classify(
			AnalysisSummary summary,
			List<ClassAnalysis> classes,
			List<EndpointAnalysis> endpoints,
			List<PackageAnalysis> packages
	) {
		return assess(summary, classes, endpoints, packages).primary();
	}

	public ArchitectureStyleAssessment assess(
			AnalysisSummary summary,
			List<ClassAnalysis> classes,
			List<EndpointAnalysis> endpoints,
			List<PackageAnalysis> packages
	) {
		if (hasPackageContaining(packages, "adapter") && hasPackageContaining(packages, "port")) {
			return new ArchitectureStyleAssessment(ArchitectureStyle.HEXAGONAL, FindingConfidence.LIKELY,
					List.of("Adapter and port package naming suggests a ports-and-adapters structure."));
		}
		if (hasPackageContaining(packages, "module") && packages.size() >= 6) {
			return new ArchitectureStyleAssessment(ArchitectureStyle.MODULAR_MONOLITH, FindingConfidence.LIKELY,
					List.of("Multiple module packages suggest modular monolith organization."));
		}
		if (hasMicroserviceMarkers(classes)) {
			return new ArchitectureStyleAssessment(ArchitectureStyle.MICROSERVICE, FindingConfidence.LIKELY,
					List.of("Microservice framework annotations were detected."));
		}
		if (isSimpleCrud(summary, endpoints)) {
			return new ArchitectureStyleAssessment(ArchitectureStyle.SIMPLE_CRUD, FindingConfidence.CONFIRMED,
					simpleCrudReasoning(summary, endpoints));
		}
		if (summary.controllers() > 0 && summary.services() > 0 && summary.repositories() > 0) {
			return new ArchitectureStyleAssessment(ArchitectureStyle.LAYERED, FindingConfidence.LIKELY,
					List.of("Controllers, services, and repositories are all present."));
		}
		return new ArchitectureStyleAssessment(ArchitectureStyle.UNKNOWN, FindingConfidence.POSSIBLE,
				List.of("No strong architecture style markers were detected."));
	}

	private boolean isSimpleCrud(AnalysisSummary summary, List<EndpointAnalysis> endpoints) {
		return summary.controllers() > 0
				&& summary.repositories() > 0
				&& summary.entities() > 0
				&& summary.services() <= summary.controllers()
				&& endpoints.stream().anyMatch(this::isCrudEndpoint);
	}

	private boolean isCrudEndpoint(EndpointAnalysis endpoint) {
		return switch (endpoint.httpMethod()) {
			case "GET", "POST", "PUT", "PATCH", "DELETE" -> true;
			default -> false;
		};
	}

	private boolean hasPackageContaining(List<PackageAnalysis> packages, String token) {
		return packages.stream()
				.anyMatch(pkg -> pkg.packageName().toLowerCase().contains(token));
	}

	private boolean hasMicroserviceMarkers(List<ClassAnalysis> classes) {
		return classes.stream()
				.flatMap(clazz -> clazz.annotations().stream())
				.anyMatch(annotation -> annotation.equals("EnableDiscoveryClient")
						|| annotation.equals("EnableEurekaClient")
						|| annotation.equals("FeignClient"));
	}

	private List<String> simpleCrudReasoning(AnalysisSummary summary, List<EndpointAnalysis> endpoints) {
		List<String> reasoning = new ArrayList<>();
		reasoning.add("Controllers and repositories are present.");
		if (summary.services() == 0) {
			reasoning.add("Minimal service abstraction.");
		} else if (summary.services() <= summary.controllers()) {
			reasoning.add("Service abstraction is limited relative to controllers.");
		}
		reasoning.add("CRUD-focused endpoint design.");
		reasoning.add("Small domain model with %d entities.".formatted(summary.entities()));
		return reasoning;
	}
}
