package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MermaidGeneratorServiceTest {

	private MermaidGeneratorService generator;

	@BeforeEach
	void setUp() {
		generator = new MermaidGeneratorService();
	}

	private ClassAnalysis classOf(String name, ComponentType type, List<String> deps) {
		return new ClassAnalysis(name, "com.app", List.of(), List.of(), deps, deps, type, null, List.of(), 0);
	}

	@Nested
	class DiagramStructure {

		@Test
		void startsWithGraphTD() {
			var diagram = generator.generateDiagram(List.of());

			assertTrue(diagram.startsWith("graph TD\n"));
		}

		@Test
		void generatesEdgesForDependencies() {
			var classes = List.of(
					classOf("Controller", ComponentType.REST_CONTROLLER, List.of("Service")),
					classOf("Service", ComponentType.SERVICE, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			assertTrue(diagram.contains("Controller --> Service"));
		}

		@Test
		void generatesMultipleEdges() {
			var classes = List.of(
					classOf("Controller", ComponentType.REST_CONTROLLER, List.of("ServiceA", "ServiceB")),
					classOf("ServiceA", ComponentType.SERVICE, List.of("Repository")),
					classOf("ServiceB", ComponentType.SERVICE, List.of()),
					classOf("Repository", ComponentType.REPOSITORY, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			assertTrue(diagram.contains("Controller --> ServiceA"));
			assertTrue(diagram.contains("Controller --> ServiceB"));
			assertTrue(diagram.contains("ServiceA --> Repository"));
		}

		@Test
		void noEdgesForClassesWithoutDependencies() {
			var classes = List.of(
					classOf("Standalone", ComponentType.SERVICE, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			assertEquals("graph TD\n", diagram);
		}
	}

	@Nested
	class Styling {

		@Test
		void appliesStyleToRestController() {
			var classes = List.of(
					classOf("MyController", ComponentType.REST_CONTROLLER, List.of("MyService")),
					classOf("MyService", ComponentType.SERVICE, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			assertTrue(diagram.contains("style MyController fill:#4CAF50,color:#fff"));
		}

		@Test
		void appliesStyleToService() {
			var classes = List.of(
					classOf("Controller", ComponentType.REST_CONTROLLER, List.of("MyService")),
					classOf("MyService", ComponentType.SERVICE, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			assertTrue(diagram.contains("style MyService fill:#2196F3,color:#fff"));
		}

		@Test
		void appliesStyleToRepository() {
			var classes = List.of(
					classOf("Service", ComponentType.SERVICE, List.of("MyRepo")),
					classOf("MyRepo", ComponentType.REPOSITORY, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			assertTrue(diagram.contains("style MyRepo fill:#FF9800,color:#fff"));
		}

		@Test
		void appliesStyleToConfiguration() {
			var classes = List.of(
					classOf("App", ComponentType.COMPONENT, List.of("AppConfig")),
					classOf("AppConfig", ComponentType.CONFIGURATION, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			assertTrue(diagram.contains("style AppConfig fill:#9C27B0,color:#fff"));
		}

		@Test
		void noStyleForUnknownType() {
			var classes = List.of(
					classOf("Known", ComponentType.SERVICE, List.of("Unknown")),
					classOf("Unknown", ComponentType.UNKNOWN, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			assertFalse(diagram.contains("style Unknown"));
		}

		@Test
		void stylesAppearAfterEdges() {
			var classes = List.of(
					classOf("Controller", ComponentType.REST_CONTROLLER, List.of("Service")),
					classOf("Service", ComponentType.SERVICE, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			int edgeIndex = diagram.indexOf("Controller --> Service");
			int styleIndex = diagram.indexOf("style Controller");

			assertTrue(edgeIndex < styleIndex);
		}
	}

	@Nested
	class EdgeCases {

		@Test
		void emptyClassListProducesOnlyHeader() {
			var diagram = generator.generateDiagram(List.of());

			assertEquals("graph TD\n", diagram);
		}

		@Test
		void dependencyOnExternalClassGetsNoStyle() {
			var classes = List.of(
					classOf("Service", ComponentType.SERVICE, List.of("ExternalThing"))
			);

			var diagram = generator.generateDiagram(classes);

			assertTrue(diagram.contains("Service --> ExternalThing"));
			assertFalse(diagram.contains("style ExternalThing"));
		}

		@Test
		void nodeAppearsOnlyOnceInStyles() {
			var classes = List.of(
					classOf("A", ComponentType.REST_CONTROLLER, List.of("B")),
					classOf("C", ComponentType.REST_CONTROLLER, List.of("B")),
					classOf("B", ComponentType.SERVICE, List.of())
			);

			var diagram = generator.generateDiagram(classes);

			int firstOccurrence = diagram.indexOf("style B ");
			int lastOccurrence = diagram.lastIndexOf("style B ");

			assertEquals(firstOccurrence, lastOccurrence);
		}
	}
}
