package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ViolationDetectorServiceTest {

	private ViolationDetectorService detector;

	@BeforeEach
	void setUp() {
		detector = new ViolationDetectorService();
	}

	private ClassAnalysis classOf(String name, String pkg, ComponentType type,
								  List<String> methods, List<String> dependencies) {
		return new ClassAnalysis(name, pkg, List.of(), methods, dependencies, dependencies,
				type, null, List.of(), dependencies.size());
	}

	@Nested
	class LayerViolations {

		@Test
		void detectsControllerBypassingServiceLayer() {
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("UserRepository"));
			var repo = classOf("UserRepository", "com.app.repository",
					ComponentType.REPOSITORY, List.of(), List.of());

			List<String> violations = detector.detect(List.of(controller, repo));

			assertEquals(1, violations.size());
			assertTrue(violations.get(0).contains("Controller bypasses service layer"));
		}

		@Test
		void detectsPlainControllerBypassingServiceLayer() {
			var controller = classOf("HomeController", "com.app.controller",
					ComponentType.CONTROLLER, List.of(), List.of("UserRepository"));
			var repo = classOf("UserRepository", "com.app.repository",
					ComponentType.REPOSITORY, List.of(), List.of());

			List<String> violations = detector.detect(List.of(controller, repo));

			assertEquals(1, violations.size());
			assertTrue(violations.get(0).contains("Controller bypasses service layer"));
		}

		@Test
		void detectsServiceDependingOnController() {
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of("UserController"));
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<String> violations = detector.detect(List.of(service, controller));

			assertEquals(1, violations.size());
			assertTrue(violations.get(0).contains("Service has reverse dependency on controller"));
		}

		@Test
		void detectsConfigurationDependingOnController() {
			var config = classOf("AppConfig", "com.app.config",
					ComponentType.CONFIGURATION, List.of(), List.of("UserController"));
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<String> violations = detector.detect(List.of(config, controller));

			assertEquals(1, violations.size());
			assertTrue(violations.get(0).contains("Configuration should not depend on controller"));
		}

		@Test
		void noViolationsForCleanArchitecture() {
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("UserService"));
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of("UserRepository"));
			var repo = classOf("UserRepository", "com.app.repository",
					ComponentType.REPOSITORY, List.of(), List.of());

			List<String> violations = detector.detect(List.of(controller, service, repo));

			assertTrue(violations.isEmpty());
		}

		@Test
		void ignoresDependenciesOnExternalClasses() {
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("SomeExternalThing"));

			List<String> violations = detector.detect(List.of(controller));

			assertTrue(violations.isEmpty());
		}
	}

	@Nested
	class CircularDependencies {

		@Test
		void detectsDirectCycle() {
			var a = classOf("ServiceA", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceB"));
			var b = classOf("ServiceB", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceA"));

			List<String> cycles = detector.detectCircularDependencies(List.of(a, b));

			assertEquals(1, cycles.size());
			assertTrue(cycles.get(0).contains("Circular dependency"));
			assertTrue(cycles.get(0).contains("ServiceA"));
			assertTrue(cycles.get(0).contains("ServiceB"));
		}

		@Test
		void doesNotDuplicateCycles() {
			var a = classOf("ServiceA", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceB"));
			var b = classOf("ServiceB", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceA"));

			List<String> cycles = detector.detectCircularDependencies(List.of(a, b));

			assertEquals(1, cycles.size());
		}

		@Test
		void noCycleWhenDependencyIsOneWay() {
			var a = classOf("ServiceA", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceB"));
			var b = classOf("ServiceB", "com.app", ComponentType.SERVICE, List.of(), List.of());

			List<String> cycles = detector.detectCircularDependencies(List.of(a, b));

			assertTrue(cycles.isEmpty());
		}
	}

	@Nested
	class GodClasses {

		@Test
		void detectsGodClassByDependencyCount() {
			var godClass = classOf("MegaService", "com.app",
					ComponentType.SERVICE, List.of("m1"),
					List.of("A", "B", "C", "D", "E", "F"));

			List<String> result = detector.detectGodClasses(List.of(godClass));

			assertEquals(1, result.size());
			assertTrue(result.get(0).contains("God class"));
			assertTrue(result.get(0).contains("6 dependencies"));
		}

		@Test
		void detectsGodClassByMethodCount() {
			List<String> methods = List.of("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10", "m11");
			var godClass = classOf("BigController", "com.app",
					ComponentType.REST_CONTROLLER, methods, List.of());

			List<String> result = detector.detectGodClasses(List.of(godClass));

			assertEquals(1, result.size());
			assertTrue(result.get(0).contains("11 methods"));
		}

		@Test
		void detectsGodClassByBothReasons() {
			List<String> methods = List.of("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10", "m11");
			var godClass = classOf("MegaService", "com.app",
					ComponentType.SERVICE, methods,
					List.of("A", "B", "C", "D", "E", "F"));

			List<String> result = detector.detectGodClasses(List.of(godClass));

			assertEquals(1, result.size());
			assertTrue(result.get(0).contains("dependencies"));
			assertTrue(result.get(0).contains("methods"));
		}

		@Test
		void doesNotFlagClassAtThreshold() {
			List<String> methods = List.of("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10");
			var normalClass = classOf("NormalService", "com.app",
					ComponentType.SERVICE, methods,
					List.of("A", "B", "C", "D", "E"));

			List<String> result = detector.detectGodClasses(List.of(normalClass));

			assertTrue(result.isEmpty());
		}
	}

	@Nested
	class EmptyControllers {

		@Test
		void detectsControllerWithNoEndpoints() {
			var controller = classOf("EmptyController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<String> result = detector.detectEmptyControllers(List.of(controller), List.of());

			assertEquals(1, result.size());
			assertTrue(result.get(0).contains("Empty controller"));
			assertTrue(result.get(0).contains("EmptyController"));
		}

		@Test
		void doesNotFlagControllerWithEndpoints() {
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());
			var endpoint = new EndpointAnalysis("UserController", "/users", "GET", "/", "getAll");

			List<String> result = detector.detectEmptyControllers(List.of(controller), List.of(endpoint));

			assertTrue(result.isEmpty());
		}

		@Test
		void ignoresNonControllerClasses() {
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of());

			List<String> result = detector.detectEmptyControllers(List.of(service), List.of());

			assertTrue(result.isEmpty());
		}
	}

	@Nested
	class OrphanServices {

		@Test
		void detectsServiceNotUsedByAnyone() {
			var orphan = classOf("DeadService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of());
			var other = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("UserService"));

			List<String> result = detector.detectOrphanServices(List.of(orphan, other));

			assertEquals(1, result.size());
			assertTrue(result.get(0).contains("Orphan service"));
			assertTrue(result.get(0).contains("DeadService"));
		}

		@Test
		void doesNotFlagServiceUsedByOtherClass() {
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of());
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("UserService"));

			List<String> result = detector.detectOrphanServices(List.of(service, controller));

			assertTrue(result.isEmpty());
		}

		@Test
		void ignoresNonServiceOrphans() {
			var component = classOf("SomeComponent", "com.app",
					ComponentType.COMPONENT, List.of(), List.of());

			List<String> result = detector.detectOrphanServices(List.of(component));

			assertTrue(result.isEmpty());
		}
	}

	@Nested
	class FatControllers {

		@Test
		void detectsControllerWithTooManyEndpoints() {
			var controller = classOf("BigController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<EndpointAnalysis> endpoints = List.of(
					new EndpointAnalysis("BigController", "/api", "GET", "/1", "m1"),
					new EndpointAnalysis("BigController", "/api", "GET", "/2", "m2"),
					new EndpointAnalysis("BigController", "/api", "POST", "/3", "m3"),
					new EndpointAnalysis("BigController", "/api", "PUT", "/4", "m4"),
					new EndpointAnalysis("BigController", "/api", "DELETE", "/5", "m5"),
					new EndpointAnalysis("BigController", "/api", "PATCH", "/6", "m6"),
					new EndpointAnalysis("BigController", "/api", "GET", "/7", "m7")
			);

			List<String> result = detector.detectFatControllers(List.of(controller), endpoints);

			assertEquals(1, result.size());
			assertTrue(result.get(0).contains("Fat controller"));
			assertTrue(result.get(0).contains("7 endpoints"));
		}

		@Test
		void doesNotFlagControllerAtThreshold() {
			var controller = classOf("NormalController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<EndpointAnalysis> endpoints = List.of(
					new EndpointAnalysis("NormalController", "/api", "GET", "/1", "m1"),
					new EndpointAnalysis("NormalController", "/api", "GET", "/2", "m2"),
					new EndpointAnalysis("NormalController", "/api", "POST", "/3", "m3"),
					new EndpointAnalysis("NormalController", "/api", "PUT", "/4", "m4"),
					new EndpointAnalysis("NormalController", "/api", "DELETE", "/5", "m5"),
					new EndpointAnalysis("NormalController", "/api", "PATCH", "/6", "m6")
			);

			List<String> result = detector.detectFatControllers(List.of(controller), endpoints);

			assertTrue(result.isEmpty());
		}

		@Test
		void ignoresNonControllerClasses() {
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of());

			List<String> result = detector.detectFatControllers(List.of(service), List.of());

			assertTrue(result.isEmpty());
		}
	}
}
