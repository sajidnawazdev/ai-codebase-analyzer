package com.isbrain.codebaseanalyzer.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
	private static final Set<String> WRITE_METHOD_NAMES = Set.of("save", "delete", "update");
	private static final Set<String> WELL_KNOWN_REPOSITORY_TYPES = Set.of(
			"JpaRepository", "CrudRepository", "PagingAndSortingRepository",
			"Repository", "ListCrudRepository", "ListPagingAndSortingRepository",
			"ReactiveCrudRepository", "ReactiveSortingRepository",
			"MongoRepository", "ReactiveMongoRepository"
	);
	private static final int CUSTOM_REPOSITORY_METHOD_THRESHOLD = 10;

	private final JavaParser javaParser = new JavaParser(new ParserConfiguration()
			.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

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

	public List<ArchitectureObservation> detectBestPracticeIssues(List<Path> javaFiles) {
		List<ArchitectureObservation> observations = new ArrayList<>();
		for (Path javaFile : javaFiles) {
			observations.addAll(detectBestPracticeIssues(javaFile));
		}
		return observations;
	}

	private List<ArchitectureObservation> detectBestPracticeIssues(Path javaFile) {
		try {
			var parseResult = javaParser.parse(javaFile);
			var compilationUnit = parseResult.getResult()
					.orElseThrow(() -> new IllegalArgumentException("Could not parse " + javaFile + ": " + parseResult.getProblems()));

			List<ArchitectureObservation> observations = new ArrayList<>();
			compilationUnit.findAll(TypeDeclaration.class)
					.stream()
					.map(td -> (TypeDeclaration<?>) td)
					.forEach(typeDeclaration -> {
						observations.addAll(detectFieldInjection(typeDeclaration));
						observations.addAll(detectMissingTransactionalOnWriteMethods(typeDeclaration));
						detectRepositoryMethodExplosion(typeDeclaration).ifPresent(observations::add);
					});
			return observations;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private List<ArchitectureObservation> detectFieldInjection(TypeDeclaration<?> typeDeclaration) {
		return typeDeclaration.getFields()
				.stream()
				.filter(field -> field.isPrivate() && hasAnnotation(field.getAnnotations().stream()
						.map(annotation -> annotation.getName().asString())
						.toList(), "Autowired"))
				.flatMap(field -> field.getVariables().stream())
				.map(variable -> new ArchitectureObservation(
						"FIELD_INJECTION",
						typeDeclaration.getName().asString(),
						"Field injection reduces testability. Prefer constructor injection.",
						70,
						true,
						FindingConfidence.POSSIBLE,
						FindingSeverity.LOW
				))
				.toList();
	}

	private List<ArchitectureObservation> detectMissingTransactionalOnWriteMethods(TypeDeclaration<?> typeDeclaration) {
		if (!hasAnnotation(annotationNames(typeDeclaration), "Service")
				|| hasAnnotation(annotationNames(typeDeclaration), "Transactional")) {
			return List.of();
		}

		return typeDeclaration.getMethods()
				.stream()
				.filter(method -> !hasAnnotation(annotationNames(method), "Transactional"))
				.filter(this::usesWriteOperation)
				.map(method -> new ArchitectureObservation(
						"MISSING_TRANSACTIONAL_ON_WRITE_METHOD",
						typeDeclaration.getName().asString(),
						"Write operation has unclear transaction boundary.",
						80,
						true,
						FindingConfidence.LIKELY,
						FindingSeverity.MEDIUM
				))
				.toList();
	}

	private java.util.Optional<ArchitectureObservation> detectRepositoryMethodExplosion(TypeDeclaration<?> typeDeclaration) {
		if (!isRepository(typeDeclaration)) {
			return java.util.Optional.empty();
		}

		int customMethodCount = typeDeclaration.getMethods().size();
		if (customMethodCount < CUSTOM_REPOSITORY_METHOD_THRESHOLD) {
			return java.util.Optional.empty();
		}

		return java.util.Optional.of(new ArchitectureObservation(
				"REPOSITORY_METHOD_EXPLOSION",
				typeDeclaration.getName().asString(),
				"Repository may be accumulating query responsibility.",
				65,
				true,
				FindingConfidence.POSSIBLE,
				FindingSeverity.LOW
		));
	}

	private boolean usesWriteOperation(MethodDeclaration method) {
		return method.findAll(MethodCallExpr.class)
				.stream()
				.map(call -> call.getName().asString())
				.anyMatch(WRITE_METHOD_NAMES::contains);
	}

	private boolean isRepository(TypeDeclaration<?> typeDeclaration) {
		if (hasAnnotation(annotationNames(typeDeclaration), "Repository")) {
			return true;
		}
		if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
			return classOrInterface.getExtendedTypes()
					.stream()
					.anyMatch(type -> WELL_KNOWN_REPOSITORY_TYPES.contains(type.getName().asString()));
		}
		return false;
	}

	private List<String> annotationNames(TypeDeclaration<?> typeDeclaration) {
		return typeDeclaration.getAnnotations()
				.stream()
				.map(annotation -> annotation.getName().asString())
				.toList();
	}

	private List<String> annotationNames(MethodDeclaration method) {
		return method.getAnnotations()
				.stream()
				.map(annotation -> annotation.getName().asString())
				.toList();
	}

	private boolean hasAnnotation(List<String> annotations, String simpleName) {
		return annotations.stream()
				.anyMatch(annotation -> annotation.equals(simpleName) || annotation.endsWith("." + simpleName));
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
