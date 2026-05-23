package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassAnalyserServiceTest {

	private ClassAnalyserService analyser;

	@BeforeEach
	void setUp() {
		analyser = new ClassAnalyserService(new EndpointExtractorService());
	}

	private Path fixture(String filename) {
		return Path.of("src/test/resources/fixtures/" + filename);
	}

	@Nested
	class FileAnalysis {

		@Test
		void parsesRestControllerClass() {
			var result = analyser.analyseJavaFile(fixture("SampleController.java"));

			assertEquals(1, result.classes().size());
			var clazz = result.classes().get(0);
			assertEquals("SampleController", clazz.className());
			assertEquals("com.example.controller", clazz.packageName());
			assertEquals(ComponentType.REST_CONTROLLER, clazz.componentType());
		}

		@Test
		void extractsAnnotations() {
			var result = analyser.analyseJavaFile(fixture("SampleController.java"));
			var clazz = result.classes().get(0);

			assertTrue(clazz.annotations().contains("RestController"));
			assertTrue(clazz.annotations().contains("RequestMapping"));
		}

		@Test
		void extractsMethods() {
			var result = analyser.analyseJavaFile(fixture("SampleService.java"));
			var clazz = result.classes().get(0);

			assertTrue(clazz.methods().contains("doWork"));
			assertTrue(clazz.methods().contains("doMoreWork"));
		}

		@Test
		void extractsDependenciesFromPrivateFinalFields() {
			var result = analyser.analyseJavaFile(fixture("SampleController.java"));
			var clazz = result.classes().get(0);

			assertEquals(2, clazz.dependencies().size());
			assertTrue(clazz.dependencies().contains("UserService"));
			assertTrue(clazz.dependencies().contains("AuditService"));
		}

		@Test
		void parsesServiceClass() {
			var result = analyser.analyseJavaFile(fixture("SampleService.java"));
			var clazz = result.classes().get(0);

			assertEquals("SampleService", clazz.className());
			assertEquals(ComponentType.SERVICE, clazz.componentType());
			assertEquals(List.of("UserRepository"), clazz.dependencies());
		}

		@Test
		void parsesInterface() {
			var result = analyser.analyseJavaFile(fixture("SampleRepository.java"));
			var clazz = result.classes().get(0);

			assertEquals("SampleRepository", clazz.className());
			assertNull(clazz.superClass());
			assertTrue(clazz.implementedInterfaces().contains("JpaRepository"));
		}

		@Test
		void parsesEntityClass() {
			var result = analyser.analyseJavaFile(fixture("SampleEntity.java"));
			var clazz = result.classes().get(0);

			assertEquals("SampleEntity", clazz.className());
			assertEquals(ComponentType.ENTITY, clazz.componentType());
		}

		@Test
		void parsesPlainClassAsUnknown() {
			var result = analyser.analyseJavaFile(fixture("PlainClass.java"));
			var clazz = result.classes().get(0);

			assertEquals("PlainClass", clazz.className());
			assertEquals(ComponentType.UNKNOWN, clazz.componentType());
		}

		@Test
		void extractsSuperClass() {
			var result = analyser.analyseJavaFile(fixture("ChildController.java"));
			var clazz = result.classes().get(0);

			assertEquals("BaseController", clazz.superClass());
		}
	}

	@Nested
	class EndpointExtraction {

		@Test
		void extractsEndpointsFromController() {
			var result = analyser.analyseJavaFile(fixture("SampleController.java"));

			assertEquals(2, result.endpoints().size());
		}

		@Test
		void endpointsHaveCorrectControllerName() {
			var result = analyser.analyseJavaFile(fixture("SampleController.java"));

			assertTrue(result.endpoints().stream()
					.allMatch(e -> e.controllerName().equals("SampleController")));
		}

		@Test
		void endpointsHaveCorrectHttpMethods() {
			var result = analyser.analyseJavaFile(fixture("SampleController.java"));

			var methods = result.endpoints().stream()
					.map(e -> e.httpMethod())
					.toList();

			assertTrue(methods.contains("GET"));
			assertTrue(methods.contains("POST"));
		}

		@Test
		void noEndpointsFromNonControllerClasses() {
			var result = analyser.analyseJavaFile(fixture("SampleService.java"));

			assertTrue(result.endpoints().isEmpty());
		}
	}

	@Nested
	class ComponentTypeDetection {

		@Test
		void detectsRestController() {
			assertEquals(ComponentType.REST_CONTROLLER,
					analyser.detectComponentType(List.of("RestController")));
		}

		@Test
		void detectsService() {
			assertEquals(ComponentType.SERVICE,
					analyser.detectComponentType(List.of("Service")));
		}

		@Test
		void detectsRepository() {
			assertEquals(ComponentType.REPOSITORY,
					analyser.detectComponentType(List.of("Repository")));
		}

		@Test
		void detectsConfiguration() {
			assertEquals(ComponentType.CONFIGURATION,
					analyser.detectComponentType(List.of("Configuration")));
		}

		@Test
		void detectsEntity() {
			assertEquals(ComponentType.ENTITY,
					analyser.detectComponentType(List.of("Entity")));
		}

		@Test
		void returnsUnknownForNoAnnotations() {
			assertEquals(ComponentType.UNKNOWN,
					analyser.detectComponentType(List.of()));
		}

		@Test
		void returnsUnknownForUnrecognizedAnnotations() {
			assertEquals(ComponentType.UNKNOWN,
					analyser.detectComponentType(List.of("Deprecated", "Override")));
		}
	}

	@Nested
	class HierarchyResolution {

		private ClassAnalysis classOf(String name, ComponentType type,
									  String superClass, List<String> interfaces) {
			return new ClassAnalysis(name, "com.app", List.of(), List.of(), List.of(), List.of(),
					type, superClass, interfaces, 0);
		}

		@Test
		void resolvesTypeFromSuperClass() {
			var parent = classOf("BaseController", ComponentType.REST_CONTROLLER, null, List.of());
			var child = classOf("ChildController", ComponentType.UNKNOWN, "BaseController", List.of());

			List<ClassAnalysis> resolved = analyser.resolveHierarchyTypes(List.of(parent, child));

			assertEquals(ComponentType.REST_CONTROLLER,
					resolved.stream().filter(c -> c.className().equals("ChildController"))
							.findFirst().get().componentType());
		}

		@Test
		void resolvesRepositoryFromWellKnownInterface() {
			var repo = classOf("UserRepository", ComponentType.UNKNOWN, null, List.of("JpaRepository"));

			List<ClassAnalysis> resolved = analyser.resolveHierarchyTypes(List.of(repo));

			assertEquals(ComponentType.REPOSITORY, resolved.get(0).componentType());
		}

		@Test
		void resolvesRepositoryFromCrudRepository() {
			var repo = classOf("UserRepository", ComponentType.UNKNOWN, null, List.of("CrudRepository"));

			List<ClassAnalysis> resolved = analyser.resolveHierarchyTypes(List.of(repo));

			assertEquals(ComponentType.REPOSITORY, resolved.get(0).componentType());
		}

		@Test
		void resolvesRepositoryFromMongoRepository() {
			var repo = classOf("UserRepository", ComponentType.UNKNOWN, null, List.of("MongoRepository"));

			List<ClassAnalysis> resolved = analyser.resolveHierarchyTypes(List.of(repo));

			assertEquals(ComponentType.REPOSITORY, resolved.get(0).componentType());
		}

		@Test
		void resolvesTypeFromInterfaceChain() {
			var baseRepo = classOf("BaseRepo", ComponentType.REPOSITORY, null, List.of());
			var customRepo = classOf("CustomRepo", ComponentType.UNKNOWN, null, List.of("BaseRepo"));

			List<ClassAnalysis> resolved = analyser.resolveHierarchyTypes(List.of(baseRepo, customRepo));

			assertEquals(ComponentType.REPOSITORY,
					resolved.stream().filter(c -> c.className().equals("CustomRepo"))
							.findFirst().get().componentType());
		}

		@Test
		void doesNotChangeAlreadyResolvedTypes() {
			var service = classOf("UserService", ComponentType.SERVICE, null, List.of());

			List<ClassAnalysis> resolved = analyser.resolveHierarchyTypes(List.of(service));

			assertEquals(ComponentType.SERVICE, resolved.get(0).componentType());
		}

		@Test
		void leavesUnresolvableAsUnknown() {
			var orphan = classOf("SomeThing", ComponentType.UNKNOWN, "MissingParent", List.of());

			List<ClassAnalysis> resolved = analyser.resolveHierarchyTypes(List.of(orphan));

			assertEquals(ComponentType.UNKNOWN, resolved.get(0).componentType());
		}

		@Test
		void resolvesMultiLevelHierarchy() {
			var grandparent = classOf("BaseController", ComponentType.REST_CONTROLLER, null, List.of());
			var parent = classOf("AbstractController", ComponentType.UNKNOWN, "BaseController", List.of());
			var child = classOf("UserController", ComponentType.UNKNOWN, "AbstractController", List.of());

			List<ClassAnalysis> resolved = analyser.resolveHierarchyTypes(
					List.of(grandparent, parent, child));

			assertEquals(ComponentType.REST_CONTROLLER,
					resolved.stream().filter(c -> c.className().equals("UserController"))
							.findFirst().get().componentType());
		}
	}
}
