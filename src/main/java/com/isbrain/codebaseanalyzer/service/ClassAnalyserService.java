package com.isbrain.codebaseanalyzer.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ClassMetrics;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassAnalyserService {

	private static final Set<String> WELL_KNOWN_REPOSITORY_TYPES = Set.of(
			"JpaRepository", "CrudRepository", "PagingAndSortingRepository",
			"Repository", "ListCrudRepository", "ListPagingAndSortingRepository",
			"ReactiveCrudRepository", "ReactiveSortingRepository",
			"MongoRepository", "ReactiveMongoRepository"
	);

	private static final Map<String, ComponentType> COMPONENT_TYPES = Map.of(
			"RestController", ComponentType.REST_CONTROLLER,
			"Controller", ComponentType.CONTROLLER,
			"Service", ComponentType.SERVICE,
			"Repository", ComponentType.REPOSITORY,
			"Component", ComponentType.COMPONENT,
			"Configuration", ComponentType.CONFIGURATION,
			"Entity", ComponentType.ENTITY,
			"ControllerAdvice", ComponentType.CONTROLLER_ADVICE,
			"RestControllerAdvice", ComponentType.REST_CONTROLLER_ADVICE
	);

	private final EndpointExtractorService endpointExtractorService;

	private final JavaParser javaParser = new JavaParser(new ParserConfiguration()
			.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

	public JavaFileAnalysis analyseJavaFile(Path path) {
		try {
			var parseResult = javaParser.parse(path);
			var compilationUnit = parseResult.getResult()
					.orElseThrow(() -> new IllegalArgumentException("Could not parse " + path + ": " + parseResult.getProblems()));
			var packageName = compilationUnit.getPackageDeclaration()
					.map(packageDeclaration -> packageDeclaration.getName().asString())
					.orElse("");

			var typeDeclarations = compilationUnit.findAll(TypeDeclaration.class)
					.stream()
					.map(td -> (TypeDeclaration<?>) td)
					.toList();

			var classes = typeDeclarations
					.stream()
					.map(typeDeclaration -> toClassAnalysis(typeDeclaration, packageName))
					.toList();

			var endpoints = typeDeclarations
					.stream()
					.flatMap(typeDeclaration -> {
						var componentType = detectComponentType(
								typeDeclaration.getAnnotations().stream()
										.map(a -> a.getName().asString())
										.toList());
						return endpointExtractorService.extractEndpoints(typeDeclaration, componentType).stream();
					})
					.toList();

			var metrics = typeDeclarations
					.stream()
					.map(typeDeclaration -> toClassMetrics(typeDeclaration, packageName))
					.toList();

			return new JavaFileAnalysis(classes, endpoints, metrics);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private ClassAnalysis toClassAnalysis(TypeDeclaration<?> typeDeclaration, String packageName) {
		var annotations = typeDeclaration.getAnnotations()
				.stream()
				.map(annotation -> annotation.getName().asString())
				.toList();

		var methods = typeDeclaration.getMethods()
				.stream()
				.map(method -> method.getName().asString())
				.toList();

		var dependencies = typeDeclaration.getFields()
				.stream()
				.filter(field -> field.isPrivate() && field.isFinal())
				.flatMap(field -> field.getVariables()
						.stream()
						.filter(variable -> variable.getInitializer().isEmpty())
						.map(variable -> variable.getType().asString()))
				.toList();

		String superClass = null;
		List<String> implementedInterfaces = List.of();

		if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
			var extendedTypes = classOrInterface.getExtendedTypes();
			if (!extendedTypes.isEmpty()) {
				superClass = extendedTypes.get(0).getName().asString();
			}
			implementedInterfaces = classOrInterface.getImplementedTypes()
					.stream()
					.map(type -> type.getName().asString())
					.toList();
			if (classOrInterface.isInterface()) {
				implementedInterfaces = extendedTypes.stream()
						.map(type -> type.getName().asString())
						.toList();
				superClass = null;
			}
		}

		return new ClassAnalysis(
				typeDeclaration.getName().asString(),
				packageName,
				annotations,
				methods,
				dependencies,
				dependencies,
				detectComponentType(annotations),
				superClass,
				implementedInterfaces,
				0
		);
	}

	private ClassMetrics toClassMetrics(TypeDeclaration<?> typeDeclaration, String packageName) {
		var annotations = typeDeclaration.getAnnotations()
				.stream()
				.map(a -> a.getName().asString())
				.toList();

		int methodCount = typeDeclaration.getMethods().size();

		int publicMethodCount = (int) typeDeclaration.getMethods()
				.stream()
				.filter(m -> m.getModifiers().stream()
						.anyMatch(mod -> mod.getKeyword() == Modifier.Keyword.PUBLIC))
				.count();

		int fieldCount = typeDeclaration.getFields().size();

		int dependencyCount = (int) typeDeclaration.getFields()
				.stream()
				.filter(field -> field.isPrivate() && field.isFinal())
				.flatMap(field -> field.getVariables()
						.stream()
						.filter(variable -> variable.getInitializer().isEmpty()))
				.count();

		int constructorParameterCount = typeDeclaration.findAll(ConstructorDeclaration.class)
				.stream()
				.mapToInt(c -> c.getParameters().size())
				.max()
				.orElse(0);

		int lineCount = typeDeclaration.getEnd()
				.map(end -> end.line - typeDeclaration.getBegin().map(b -> b.line).orElse(1) + 1)
				.orElse(0);

		return new ClassMetrics(
				typeDeclaration.getName().asString(),
				packageName,
				detectComponentType(annotations),
				methodCount,
				publicMethodCount,
				fieldCount,
				dependencyCount,
				constructorParameterCount,
				lineCount
		);
	}

	ComponentType detectComponentType(List<String> annotations) {
		return annotations.stream()
				.map(COMPONENT_TYPES::get)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(ComponentType.UNKNOWN);
	}

	public List<ClassAnalysis> resolveHierarchyTypes(List<ClassAnalysis> classes) {
		Map<String, ComponentType> typeByName = classes.stream()
				.collect(Collectors.toMap(ClassAnalysis::className, ClassAnalysis::componentType, (a, b) -> a));

		boolean changed = true;
		while (changed) {
			changed = false;
			for (ClassAnalysis clazz : classes) {
				if (typeByName.get(clazz.className()) != ComponentType.UNKNOWN) {
					continue;
				}
				ComponentType resolved = resolveFromHierarchy(clazz, typeByName);
				if (resolved != ComponentType.UNKNOWN) {
					typeByName.put(clazz.className(), resolved);
					changed = true;
				}
			}
		}

		return classes.stream()
				.map(clazz -> {
					ComponentType resolved = typeByName.get(clazz.className());
					if (resolved == clazz.componentType()) {
						return clazz;
					}
					return new ClassAnalysis(
							clazz.className(), clazz.packageName(), clazz.annotations(),
							clazz.methods(), clazz.dependencies(), clazz.allDependencies(),
							resolved, clazz.superClass(), clazz.implementedInterfaces(),
							clazz.couplingScore()
					);
				})
				.toList();
	}

	private ComponentType resolveFromHierarchy(ClassAnalysis clazz, Map<String, ComponentType> typeByName) {
		if (clazz.superClass() != null) {
			ComponentType parentType = typeByName.getOrDefault(clazz.superClass(), ComponentType.UNKNOWN);
			if (parentType != ComponentType.UNKNOWN) {
				return parentType;
			}
		}

		for (String iface : clazz.implementedInterfaces()) {
			if (WELL_KNOWN_REPOSITORY_TYPES.contains(iface)) {
				return ComponentType.REPOSITORY;
			}
			ComponentType ifaceType = typeByName.getOrDefault(iface, ComponentType.UNKNOWN);
			if (ifaceType != ComponentType.UNKNOWN) {
				return ifaceType;
			}
		}

		return ComponentType.UNKNOWN;
	}

	public record JavaFileAnalysis(List<ClassAnalysis> classes, List<EndpointAnalysis> endpoints,
								   List<ClassMetrics> metrics) {
	}
}
