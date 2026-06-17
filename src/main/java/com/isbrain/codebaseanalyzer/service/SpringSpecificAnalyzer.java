package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SpringSpecificAnalyzer {

	private static final Set<String> PAGINATION_PARAMETER_NAMES = Set.of(
			"page", "size", "limit", "offset", "pageable", "pagination"
	);
	private static final Set<String> EXCLUDED_PATHS = Set.of(
			"/oups", "/error", "/health", "/login", "/logout", "/", "/welcome"
	);

	public List<ArchitectureObservation> detectListEndpointsWithoutPagination(List<EndpointAnalysis> endpoints) {
		return endpoints.stream()
				.filter(this::isUnpaginatedListEndpoint)
				.map(endpoint -> new ArchitectureObservation(
						"LIST_ENDPOINT_WITHOUT_PAGINATION",
						endpoint.controllerName(),
						"%s %s may return multiple %s without explicit pagination.".formatted(
								endpoint.httpMethod(),
								endpoint.endpointPath(),
								resourceName(endpoint.endpointPath())),
						60,
						true,
						FindingConfidence.POSSIBLE,
						FindingSeverity.LOW
				))
				.toList();
	}

	private boolean isUnpaginatedListEndpoint(EndpointAnalysis endpoint) {
		return "GET".equals(endpoint.httpMethod())
				&& !endpoint.endpointPath().contains("{")
				&& !isExcludedRoute(endpoint.endpointPath())
				&& isCollectionLikePath(endpoint.endpointPath())
				&& endpoint.collectionResponseSignal()
				&& !hasPaginationParameter(endpoint);
	}

	private boolean isExcludedRoute(String path) {
		String normalized = normalizePath(path);
		return EXCLUDED_PATHS.contains(normalized) || normalized.startsWith("/actuator/");
	}

	private boolean isCollectionLikePath(String path) {
		String segment = lastPathSegment(path);
		return segment.length() > 1 && segment.endsWith("s");
	}

	private boolean hasPaginationParameter(EndpointAnalysis endpoint) {
		return endpoint.parameterTypes().stream()
				.anyMatch(type -> type.endsWith("Pageable") || type.endsWith("PageRequest"))
				|| endpoint.parameterNames().stream()
				.map(name -> name.toLowerCase(Locale.ROOT))
				.anyMatch(PAGINATION_PARAMETER_NAMES::contains);
	}

	private String resourceName(String path) {
		String segment = lastPathSegment(path);
		return segment.isBlank() ? "resources" : segment;
	}

	private String lastPathSegment(String path) {
		String normalized = normalizePath(path);
		if (normalized.isBlank()) {
			return "";
		}
		int slash = normalized.lastIndexOf('/');
		return slash >= 0 ? normalized.substring(slash + 1) : normalized;
	}

	private String normalizePath(String path) {
		if (path == null || path.isBlank()) {
			return "";
		}
		String normalized = path.startsWith("/") ? path : "/" + path;
		return normalized.length() > 1 && normalized.endsWith("/")
				? normalized.substring(0, normalized.length() - 1)
				: normalized;
	}
}
