package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ClassMetrics;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureObservationDetectorServiceTest {

	private ArchitectureObservationDetectorService detector;

	@BeforeEach
	void setUp() {
		detector = new ArchitectureObservationDetectorService();
	}

	private ClassAnalysis classOf(String name, String pkg, ComponentType type,
								  List<String> methods, List<String> dependencies) {
		return new ClassAnalysis(name, pkg, List.of(), methods, dependencies, dependencies,
				type, null, List.of(), dependencies.size());
	}

	private ClassMetrics metrics(String name, ComponentType type, int methods, int publicMethods,
								 int dependencies, int lines) {
		return new ClassMetrics(name, "com.app", type, methods, publicMethods,
				dependencies, dependencies, dependencies, lines);
	}

	@Nested
	class LayeringObservations {

		@Test
		void detectsControllerBypassingServiceLayer() {
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("UserRepository"));
			var repo = classOf("UserRepository", "com.app.repository",
					ComponentType.REPOSITORY, List.of(), List.of());

			List<ArchitectureObservation> observations = detector.detect(List.of(controller, repo));

			assertEquals(1, observations.size());
			assertEquals("LAYERING", observations.get(0).type());
			assertEquals("UserController", observations.get(0).className());
			assertTrue(observations.get(0).description().contains("UserRepository"));
			assertEquals(FindingConfidence.POSSIBLE, observations.get(0).findingConfidence());
			assertEquals(FindingSeverity.LOW, observations.get(0).severity());
		}

		@Test
		void detectsPlainControllerBypassingServiceLayer() {
			var controller = classOf("HomeController", "com.app.controller",
					ComponentType.CONTROLLER, List.of(), List.of("UserRepository"));
			var repo = classOf("UserRepository", "com.app.repository",
					ComponentType.REPOSITORY, List.of(), List.of());

			List<ArchitectureObservation> observations = detector.detect(List.of(controller, repo));

			assertEquals(1, observations.size());
			assertEquals("LAYERING", observations.get(0).type());
			assertEquals("HomeController", observations.get(0).className());
		}

		@Test
		void detectsServiceDependingOnController() {
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of("UserController"));
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<ArchitectureObservation> observations = detector.detect(List.of(service, controller));

			assertEquals(1, observations.size());
			assertEquals("LAYERING", observations.get(0).type());
			assertEquals("UserService", observations.get(0).className());
			assertTrue(observations.get(0).description().contains("UserController"));
		}

		@Test
		void detectsConfigurationDependingOnController() {
			var config = classOf("AppConfig", "com.app.config",
					ComponentType.CONFIGURATION, List.of(), List.of("UserController"));
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<ArchitectureObservation> observations = detector.detect(List.of(config, controller));

			assertEquals(1, observations.size());
			assertEquals("LAYERING", observations.get(0).type());
			assertEquals("AppConfig", observations.get(0).className());
		}

		@Test
		void noObservationsForCleanArchitecture() {
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("UserService"));
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of("UserRepository"));
			var repo = classOf("UserRepository", "com.app.repository",
					ComponentType.REPOSITORY, List.of(), List.of());

			List<ArchitectureObservation> observations = detector.detect(List.of(controller, service, repo));

			assertTrue(observations.isEmpty());
		}

		@Test
		void ignoresDependenciesOnExternalClasses() {
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("SomeExternalThing"));

			List<ArchitectureObservation> observations = detector.detect(List.of(controller));

			assertTrue(observations.isEmpty());
		}
	}

	@Nested
	class CircularDependencies {

		@Test
		void detectsDirectCycle() {
			var a = classOf("ServiceA", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceB"));
			var b = classOf("ServiceB", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceA"));

			List<ArchitectureObservation> observations = detector.detectCircularDependencies(List.of(a, b));

			assertEquals(1, observations.size());
			assertEquals("CIRCULAR_DEPENDENCY", observations.get(0).type());
			assertTrue(observations.get(0).description().contains("ServiceA"));
			assertTrue(observations.get(0).description().contains("ServiceB"));
			assertEquals(FindingConfidence.CONFIRMED, observations.get(0).findingConfidence());
			assertEquals(FindingSeverity.HIGH, observations.get(0).severity());
		}

		@Test
		void doesNotDuplicateCycles() {
			var a = classOf("ServiceA", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceB"));
			var b = classOf("ServiceB", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceA"));

			List<ArchitectureObservation> observations = detector.detectCircularDependencies(List.of(a, b));

			assertEquals(1, observations.size());
		}

		@Test
		void noCycleWhenDependencyIsOneWay() {
			var a = classOf("ServiceA", "com.app", ComponentType.SERVICE, List.of(), List.of("ServiceB"));
			var b = classOf("ServiceB", "com.app", ComponentType.SERVICE, List.of(), List.of());

			List<ArchitectureObservation> observations = detector.detectCircularDependencies(List.of(a, b));

			assertTrue(observations.isEmpty());
		}
	}

	@Nested
	class GodClasses {

		@Test
		void detectsGodClassByDependencyCount() {
			var godClass = classOf("MegaService", "com.app",
					ComponentType.SERVICE, List.of("m1"),
					List.of("A", "B", "C", "D", "E", "F", "G", "H"));

			List<ArchitectureObservation> result = detector.detectGodClasses(List.of(godClass),
					List.of(metrics("MegaService", ComponentType.SERVICE, 1, 1, 8, 100)));

			assertEquals(1, result.size());
			assertEquals("GOD_CLASS", result.get(0).type());
			assertTrue(result.get(0).description().contains("8 dependencies"));
			assertEquals(FindingSeverity.HIGH, result.get(0).severity());
		}

		@Test
		void detectsGodClassByMethodCount() {
			List<String> methods = new ArrayList<>();
			for (int i = 1; i <= 25; i++) methods.add("m" + i);
			var godClass = classOf("BigService", "com.app",
					ComponentType.SERVICE, methods, List.of());

			List<ArchitectureObservation> result = detector.detectGodClasses(List.of(godClass),
					List.of(metrics("BigService", ComponentType.SERVICE, 25, 25, 0, 100)));

			assertEquals(1, result.size());
			assertEquals("GOD_CLASS", result.get(0).type());
			assertTrue(result.get(0).description().contains("25 methods"));
		}

		@Test
		void detectsGodClassByLineCount() {
			var godClass = classOf("LargeService", "com.app",
					ComponentType.SERVICE, List.of("m1"), List.of());

			List<ArchitectureObservation> result = detector.detectGodClasses(List.of(godClass),
					List.of(metrics("LargeService", ComponentType.SERVICE, 1, 1, 0, 500)));

			assertEquals(1, result.size());
			assertEquals("GOD_CLASS", result.get(0).type());
			assertTrue(result.get(0).description().contains("500 LOC"));
		}

		@Test
		void detectsGodClassByMultipleReasons() {
			List<String> methods = new ArrayList<>();
			for (int i = 1; i <= 25; i++) methods.add("m" + i);
			var godClass = classOf("MegaService", "com.app",
					ComponentType.SERVICE, methods,
					List.of("A", "B", "C", "D", "E", "F", "G", "H"));

			List<ArchitectureObservation> result = detector.detectGodClasses(List.of(godClass),
					List.of(metrics("MegaService", ComponentType.SERVICE, 25, 25, 8, 500)));

			assertEquals(1, result.size());
			assertTrue(result.get(0).description().contains("dependencies"));
			assertTrue(result.get(0).description().contains("methods"));
			assertEquals(FindingConfidence.CONFIRMED, result.get(0).findingConfidence());
		}

		@Test
		void doesNotFlagClassBelowThreshold() {
			List<String> methods = java.util.stream.IntStream.rangeClosed(1, 24)
					.mapToObj(i -> "m" + i)
					.toList();
			var normalClass = classOf("NormalService", "com.app",
					ComponentType.SERVICE, methods,
					List.of("A", "B", "C", "D", "E", "F", "G"));

			List<ArchitectureObservation> result = detector.detectGodClasses(List.of(normalClass),
					List.of(metrics("NormalService", ComponentType.SERVICE, 24, 24, 7, 499)));

			assertTrue(result.isEmpty());
		}

		@Test
		void classifiesThirteenMethodEntityAsDomainEntityComplexity() {
			List<String> methods = java.util.stream.IntStream.rangeClosed(1, 13)
					.mapToObj(i -> "m" + i)
					.toList();
			var entity = classOf("Owner", "com.app",
					ComponentType.ENTITY, methods, List.of());

			List<ArchitectureObservation> result = detector.detectGodClasses(List.of(entity),
					List.of(metrics("Owner", ComponentType.ENTITY, 13, 13, 0, 120)));

			assertEquals(1, result.size());
			assertEquals("DOMAIN_ENTITY_COMPLEXITY", result.get(0).type());
			assertEquals(FindingSeverity.LOW, result.get(0).severity());
			assertEquals(FindingConfidence.POSSIBLE, result.get(0).findingConfidence());
		}
	}

	@Nested
	class EmptyControllers {

		@Test
		void detectsControllerWithNoEndpoints() {
			var controller = classOf("EmptyController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<ArchitectureObservation> result = detector.detectEmptyControllers(List.of(controller), List.of());

			assertEquals(1, result.size());
			assertEquals("EMPTY_CONTROLLER", result.get(0).type());
			assertEquals("EmptyController", result.get(0).className());
		}

		@Test
		void doesNotFlagControllerWithEndpoints() {
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());
			var endpoint = new EndpointAnalysis("UserController", "/users", "GET", "/", "getAll");

			List<ArchitectureObservation> result = detector.detectEmptyControllers(List.of(controller), List.of(endpoint));

			assertTrue(result.isEmpty());
		}

		@Test
		void ignoresNonControllerClasses() {
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of());

			List<ArchitectureObservation> result = detector.detectEmptyControllers(List.of(service), List.of());

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

			List<ArchitectureObservation> result = detector.detectOrphanServices(List.of(orphan, other));

			assertEquals(1, result.size());
			assertEquals("ORPHAN_SERVICE", result.get(0).type());
			assertEquals("DeadService", result.get(0).className());
		}

		@Test
		void doesNotFlagServiceUsedByOtherClass() {
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of());
			var controller = classOf("UserController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("UserService"));

			List<ArchitectureObservation> result = detector.detectOrphanServices(List.of(service, controller));

			assertTrue(result.isEmpty());
		}

		@Test
		void ignoresNonServiceOrphans() {
			var component = classOf("SomeComponent", "com.app",
					ComponentType.COMPONENT, List.of(), List.of());

			List<ArchitectureObservation> result = detector.detectOrphanServices(List.of(component));

			assertTrue(result.isEmpty());
		}
	}

	@Nested
	class FatControllers {

		@Test
		void detectsControllerWithTooManyEndpoints() {
			var controller = classOf("BigController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<EndpointAnalysis> endpoints = new ArrayList<>();
			for (int i = 1; i <= 16; i++) {
				endpoints.add(new EndpointAnalysis("BigController", "/api", "GET", "/" + i, "m" + i));
			}

			List<ArchitectureObservation> result = detector.detectFatControllers(List.of(controller), endpoints, List.of());

			assertEquals(1, result.size());
			assertEquals("FAT_CONTROLLER", result.get(0).type());
			assertTrue(result.get(0).description().contains("16 endpoints"));
		}

		@Test
		void doesNotFlagControllerAtThreshold() {
			var controller = classOf("NormalController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<EndpointAnalysis> endpoints = new ArrayList<>();
			for (int i = 1; i <= 15; i++) {
				endpoints.add(new EndpointAnalysis("NormalController", "/api", "GET", "/" + i, "m" + i));
			}

			List<ArchitectureObservation> result = detector.detectFatControllers(List.of(controller), endpoints, List.of());

			assertEquals(1, result.size());
		}

		@Test
		void doesNotFlagControllerWithTenMethods() {
			List<String> methods = java.util.stream.IntStream.rangeClosed(1, 10)
					.mapToObj(i -> "m" + i)
					.toList();
			var controller = classOf("PetController", "com.app.controller",
					ComponentType.REST_CONTROLLER, methods, List.of());

			List<EndpointAnalysis> endpoints = new ArrayList<>();
			for (int i = 1; i <= 10; i++) {
				endpoints.add(new EndpointAnalysis("PetController", "/pets", "GET", "/" + i, "m" + i));
			}

			List<ArchitectureObservation> result = detector.detectFatControllers(List.of(controller), endpoints,
					List.of(metrics("PetController", ComponentType.REST_CONTROLLER, 10, 10, 1, 180)));

			assertTrue(result.isEmpty());
		}

		@Test
		void detectsControllerWithTooManyDependencies() {
			var controller = classOf("AdminController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of("A", "B", "C", "D", "E", "F"));

			List<ArchitectureObservation> result = detector.detectFatControllers(List.of(controller), List.of(),
					List.of(metrics("AdminController", ComponentType.REST_CONTROLLER, 1, 1, 6, 120)));

			assertEquals(1, result.size());
			assertTrue(result.get(0).description().contains("6 dependencies"));
		}

		@Test
		void detectsControllerWithLargeLoc() {
			var controller = classOf("AdminController", "com.app.controller",
					ComponentType.REST_CONTROLLER, List.of(), List.of());

			List<ArchitectureObservation> result = detector.detectFatControllers(List.of(controller), List.of(),
					List.of(metrics("AdminController", ComponentType.REST_CONTROLLER, 1, 1, 0, 400)));

			assertEquals(1, result.size());
			assertTrue(result.get(0).description().contains("400 LOC"));
		}

		@Test
		void ignoresNonControllerClasses() {
			var service = classOf("UserService", "com.app.service",
					ComponentType.SERVICE, List.of(), List.of());

			List<ArchitectureObservation> result = detector.detectFatControllers(List.of(service), List.of(), List.of());

			assertTrue(result.isEmpty());
		}
	}
}
