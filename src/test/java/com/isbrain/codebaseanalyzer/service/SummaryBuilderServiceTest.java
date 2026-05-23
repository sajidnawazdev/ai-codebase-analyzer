package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SummaryBuilderServiceTest {

	private SummaryBuilderService builder;

	@BeforeEach
	void setUp() {
		builder = new SummaryBuilderService();
	}

	private ClassAnalysis classOf(String name, String pkg, ComponentType type, List<String> deps) {
		return new ClassAnalysis(name, pkg, List.of(), List.of(), deps, deps, type, null, List.of(), 0);
	}

	@Nested
	class BuildSummary {

		@Test
		void countsRestControllerAndControllerTogether() {
			var classes = List.of(
					classOf("A", "com.app", ComponentType.REST_CONTROLLER, List.of()),
					classOf("B", "com.app", ComponentType.CONTROLLER, List.of()),
					classOf("C", "com.app", ComponentType.REST_CONTROLLER, List.of())
			);

			var summary = builder.buildSummary(classes);

			assertEquals(3, summary.controllers());
		}

		@Test
		void countsServices() {
			var classes = List.of(
					classOf("S1", "com.app", ComponentType.SERVICE, List.of()),
					classOf("S2", "com.app", ComponentType.SERVICE, List.of())
			);

			var summary = builder.buildSummary(classes);

			assertEquals(2, summary.services());
		}

		@Test
		void countsRepositories() {
			var classes = List.of(
					classOf("R1", "com.app", ComponentType.REPOSITORY, List.of())
			);

			var summary = builder.buildSummary(classes);

			assertEquals(1, summary.repositories());
		}

		@Test
		void countsComponents() {
			var classes = List.of(
					classOf("C1", "com.app", ComponentType.COMPONENT, List.of())
			);

			var summary = builder.buildSummary(classes);

			assertEquals(1, summary.components());
		}

		@Test
		void countsConfigurations() {
			var classes = List.of(
					classOf("Cfg", "com.app", ComponentType.CONFIGURATION, List.of())
			);

			var summary = builder.buildSummary(classes);

			assertEquals(1, summary.configurations());
		}

		@Test
		void countsEntities() {
			var classes = List.of(
					classOf("E1", "com.app", ComponentType.ENTITY, List.of()),
					classOf("E2", "com.app", ComponentType.ENTITY, List.of())
			);

			var summary = builder.buildSummary(classes);

			assertEquals(2, summary.entities());
		}

		@Test
		void countsControllerAdvices() {
			var classes = List.of(
					classOf("CA", "com.app", ComponentType.CONTROLLER_ADVICE, List.of())
			);

			var summary = builder.buildSummary(classes);

			assertEquals(1, summary.controllerAdvices());
		}

		@Test
		void countsRestControllerAdvices() {
			var classes = List.of(
					classOf("RCA", "com.app", ComponentType.REST_CONTROLLER_ADVICE, List.of())
			);

			var summary = builder.buildSummary(classes);

			assertEquals(1, summary.restControllerAdvices());
		}

		@Test
		void returnsZerosForEmptyList() {
			var summary = builder.buildSummary(List.of());

			assertEquals(0, summary.controllers());
			assertEquals(0, summary.services());
			assertEquals(0, summary.repositories());
			assertEquals(0, summary.components());
			assertEquals(0, summary.configurations());
			assertEquals(0, summary.entities());
			assertEquals(0, summary.controllerAdvices());
			assertEquals(0, summary.restControllerAdvices());
		}

		@Test
		void doesNotCountUnknownTypes() {
			var classes = List.of(
					classOf("X", "com.app", ComponentType.UNKNOWN, List.of()),
					classOf("S", "com.app", ComponentType.SERVICE, List.of())
			);

			var summary = builder.buildSummary(classes);

			assertEquals(1, summary.services());
			assertEquals(0, summary.controllers());
			assertEquals(0, summary.repositories());
		}
	}

	@Nested
	class BuildRelationships {

		@Test
		void buildsDependencyStrings() {
			var classes = List.of(
					classOf("UserController", "com.app", ComponentType.REST_CONTROLLER, List.of("UserService")),
					classOf("UserService", "com.app", ComponentType.SERVICE, List.of("UserRepository"))
			);

			var relationships = builder.buildRelationships(classes);

			assertEquals(2, relationships.size());
			assertTrue(relationships.contains("UserController -> UserService"));
			assertTrue(relationships.contains("UserService -> UserRepository"));
		}

		@Test
		void handlesMultipleDependencies() {
			var classes = List.of(
					classOf("Controller", "com.app", ComponentType.REST_CONTROLLER,
							List.of("ServiceA", "ServiceB"))
			);

			var relationships = builder.buildRelationships(classes);

			assertEquals(2, relationships.size());
			assertTrue(relationships.contains("Controller -> ServiceA"));
			assertTrue(relationships.contains("Controller -> ServiceB"));
		}

		@Test
		void returnsEmptyForNoDependencies() {
			var classes = List.of(
					classOf("Standalone", "com.app", ComponentType.SERVICE, List.of())
			);

			var relationships = builder.buildRelationships(classes);

			assertTrue(relationships.isEmpty());
		}

		@Test
		void returnsEmptyForEmptyClassList() {
			var relationships = builder.buildRelationships(List.of());

			assertTrue(relationships.isEmpty());
		}
	}

	@Nested
	class BuildPackages {

		@Test
		void groupsClassesByPackage() {
			var classes = List.of(
					classOf("A", "com.app.controller", ComponentType.REST_CONTROLLER, List.of()),
					classOf("B", "com.app.controller", ComponentType.REST_CONTROLLER, List.of()),
					classOf("C", "com.app.service", ComponentType.SERVICE, List.of())
			);

			var packages = builder.buildPackages(classes);

			assertEquals(2, packages.size());
		}

		@Test
		void setsCorrectClassCount() {
			var classes = List.of(
					classOf("A", "com.app.controller", ComponentType.REST_CONTROLLER, List.of()),
					classOf("B", "com.app.controller", ComponentType.CONTROLLER, List.of())
			);

			var packages = builder.buildPackages(classes);

			var controllerPkg = packages.stream()
					.filter(p -> p.packageName().equals("com.app.controller"))
					.findFirst().orElseThrow();

			assertEquals(2, controllerPkg.classCount());
		}

		@Test
		void buildsComponentTypeDistribution() {
			var classes = List.of(
					classOf("A", "com.app.service", ComponentType.SERVICE, List.of()),
					classOf("B", "com.app.service", ComponentType.SERVICE, List.of()),
					classOf("C", "com.app.service", ComponentType.COMPONENT, List.of())
			);

			var packages = builder.buildPackages(classes);

			var servicePkg = packages.stream()
					.filter(p -> p.packageName().equals("com.app.service"))
					.findFirst().orElseThrow();

			assertEquals(2L, servicePkg.componentTypeDistribution().get(ComponentType.SERVICE));
			assertEquals(1L, servicePkg.componentTypeDistribution().get(ComponentType.COMPONENT));
		}

		@Test
		void returnsEmptyForEmptyClassList() {
			var packages = builder.buildPackages(List.of());

			assertTrue(packages.isEmpty());
		}

		@Test
		void handlesSinglePackage() {
			var classes = List.of(
					classOf("A", "com.app", ComponentType.SERVICE, List.of())
			);

			var packages = builder.buildPackages(classes);

			assertEquals(1, packages.size());
			assertEquals("com.app", packages.get(0).packageName());
			assertEquals(1, packages.get(0).classCount());
		}
	}
}
