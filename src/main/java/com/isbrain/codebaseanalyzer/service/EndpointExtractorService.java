package com.isbrain.codebaseanalyzer.service;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EndpointExtractorService {

	private static final Map<String, String> HTTP_METHOD_MAPPINGS = Map.of(
			"GetMapping", "GET",
			"PostMapping", "POST",
			"PutMapping", "PUT",
			"DeleteMapping", "DELETE",
			"PatchMapping", "PATCH"
	);

	public List<EndpointAnalysis> extractEndpoints(TypeDeclaration<?> typeDeclaration, ComponentType componentType) {
		if (componentType != ComponentType.REST_CONTROLLER && componentType != ComponentType.CONTROLLER) {
			return List.of();
		}

		var basePath = typeDeclaration.getAnnotationByName("RequestMapping")
				.flatMap(this::extractPath)
				.orElse("");

		return typeDeclaration.getMethods()
				.stream()
				.flatMap(method -> toEndpointAnalysis(typeDeclaration, method, basePath).stream())
				.toList();
	}

	private Optional<EndpointAnalysis> toEndpointAnalysis(TypeDeclaration<?> typeDeclaration, MethodDeclaration method, String basePath) {
		return method.getAnnotations()
				.stream()
				.map(annotation -> toEndpointAnalysis(typeDeclaration, method, basePath, annotation))
				.flatMap(Optional::stream)
				.findFirst();
	}

	private Optional<EndpointAnalysis> toEndpointAnalysis(
			TypeDeclaration<?> typeDeclaration,
			MethodDeclaration method,
			String basePath,
			AnnotationExpr annotation
	) {
		var annotationName = annotation.getName().asString();
		var httpMethod = HTTP_METHOD_MAPPINGS.get(annotationName);

		if (httpMethod == null && annotationName.equals("RequestMapping")) {
			httpMethod = extractRequestMappingMethod(annotation).orElse(null);
		}

		if (httpMethod == null) {
			return Optional.empty();
		}

		var methodPath = extractPath(annotation).orElse("");

		return Optional.of(new EndpointAnalysis(
				typeDeclaration.getName().asString(),
				basePath,
				httpMethod,
				joinPaths(basePath, methodPath),
				method.getName().asString(),
				method.getType().asString(),
				method.getParameters().stream()
						.map(parameter -> parameter.getType().asString())
						.toList(),
				method.getParameters().stream()
						.map(parameter -> parameter.getName().asString())
						.toList(),
				hasCollectionResponseSignal(method)
		));
	}

	private boolean hasCollectionResponseSignal(MethodDeclaration method) {
		String returnType = method.getType().asString();
		if (isCollectionReturnType(returnType)) {
			return true;
		}
		if (isCollectionHandlerName(method.getName().asString())) {
			return true;
		}
		return method.getBody()
				.map(body -> {
					String bodyText = body.toString().toLowerCase();
					return bodyText.contains("addattribute")
							&& (bodyText.contains("list")
							|| bodyText.contains("collection")
							|| bodyText.contains("iterable")
							|| bodyText.contains("findall")
							|| bodyText.contains("findby")
							|| bodyText.contains("results")
							|| bodyText.contains("selections"));
				})
				.orElse(false);
	}

	private boolean isCollectionReturnType(String returnType) {
		String normalized = returnType.replace("[]", "Array").toLowerCase();
		return normalized.startsWith("list<")
				|| normalized.startsWith("page<")
				|| normalized.startsWith("slice<")
				|| normalized.startsWith("collection<")
				|| normalized.startsWith("iterable<")
				|| normalized.endsWith("array");
	}

	private boolean isCollectionHandlerName(String methodName) {
		String normalized = methodName.toLowerCase();
		return normalized.contains("findall")
				|| normalized.contains("list")
				|| normalized.contains("search")
				|| normalized.contains("getall");
	}

	private Optional<String> extractPath(AnnotationExpr annotation) {
		if (annotation.isSingleMemberAnnotationExpr()) {
			return extractStringValue(annotation.asSingleMemberAnnotationExpr().getMemberValue());
		}

		if (annotation.isNormalAnnotationExpr()) {
			return annotation.asNormalAnnotationExpr()
					.getPairs()
					.stream()
					.filter(pair -> pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path"))
					.findFirst()
					.flatMap(pair -> extractStringValue(pair.getValue()));
		}

		return Optional.empty();
	}

	private Optional<String> extractStringValue(Expression expression) {
		if (expression.isStringLiteralExpr()) {
			return Optional.of(expression.asStringLiteralExpr().asString());
		}

		return Optional.empty();
	}

	private Optional<String> extractRequestMappingMethod(AnnotationExpr annotation) {
		if (!annotation.isNormalAnnotationExpr()) {
			return Optional.empty();
		}

		return annotation.asNormalAnnotationExpr()
				.getPairs()
				.stream()
				.filter(pair -> pair.getNameAsString().equals("method"))
				.findFirst()
				.flatMap(pair -> extractHttpMethod(pair.getValue()));
	}

	private Optional<String> extractHttpMethod(Expression expression) {
		if (expression.isFieldAccessExpr()) {
			return Optional.of(expression.asFieldAccessExpr().getNameAsString());
		}
		if (expression.isNameExpr()) {
			return Optional.of(expression.asNameExpr().getNameAsString());
		}

		return Optional.empty();
	}

	private String joinPaths(String basePath, String methodPath) {
		if (basePath.isBlank()) {
			return normalizePath(methodPath);
		}
		if (methodPath.isBlank()) {
			return normalizePath(basePath);
		}

		return normalizePath(basePath) + normalizePath(methodPath);
	}

	private String normalizePath(String path) {
		if (path.isBlank()) {
			return "";
		}

		return path.startsWith("/") ? path : "/" + path;
	}
}
