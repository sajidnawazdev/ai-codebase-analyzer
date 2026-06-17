package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitecturalHotspot;
import com.isbrain.codebaseanalyzer.model.ClassMetrics;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureHotspotAnalyzerTest {

	private ArchitectureHotspotAnalyzer analyzer;

	@BeforeEach
	void setUp() {
		analyzer = new ArchitectureHotspotAnalyzer();
	}

	private ClassMetrics metrics(String className, int methods, int publicMethods,
								 int fields, int dependencies, int constructorParams, int lines) {
		return new ClassMetrics(
				className,
				"com.app",
				ComponentType.SERVICE,
				methods,
				publicMethods,
				fields,
				dependencies,
				constructorParams,
				lines
		);
	}

	@Nested
	class HotspotRules {

		@Test
		void detectsHighCoupling() {
			List<ArchitecturalHotspot> hotspots = analyzer.analyze(List.of(
					metrics("OrderService", 5, 5, 6, 6, 3, 120)
			));

			assertTrue(hasType(hotspots, "HIGH_COUPLING"));
		}

		@Test
		void detectsLargeClass() {
			List<ArchitecturalHotspot> hotspots = analyzer.analyze(List.of(
					metrics("LargeService", 12, 8, 4, 3, 3, 500)
			));

			assertTrue(hasType(hotspots, "LARGE_CLASS"));
		}

		@Test
		void detectsLargePublicApi() {
			List<ArchitecturalHotspot> hotspots = analyzer.analyze(List.of(
					metrics("InvoiceService", 20, 15, 4, 3, 3, 200)
			));

			assertTrue(hasType(hotspots, "LARGE_PUBLIC_API"));
		}

		@Test
		void detectsHighDependencyDensity() {
			List<ArchitecturalHotspot> hotspots = analyzer.analyze(List.of(
					metrics("CoordinatorService", 4, 4, 5, 5, 5, 160)
			));

			assertTrue(hasType(hotspots, "HIGH_DEPENDENCY_DENSITY"));
		}

		@Test
		void detectsMassiveConstructor() {
			List<ArchitecturalHotspot> hotspots = analyzer.analyze(List.of(
					metrics("WorkflowService", 8, 5, 7, 7, 7, 240)
			));

			assertTrue(hasType(hotspots, "MASSIVE_CONSTRUCTOR"));
		}
	}

	@Test
	void ignoresClassesBelowThresholds() {
		List<ArchitecturalHotspot> hotspots = analyzer.analyze(List.of(
				metrics("SmallService", 5, 5, 3, 3, 3, 120)
		));

		assertTrue(hotspots.isEmpty());
	}

	@Test
	void sortsHighestRiskFirst() {
		List<ArchitecturalHotspot> hotspots = analyzer.analyze(List.of(
				metrics("LargeService", 12, 8, 4, 3, 3, 500),
				metrics("VeryLargeService", 12, 8, 4, 3, 3, 900)
		));

		assertFalse(hotspots.isEmpty());
		assertEquals("VeryLargeService", hotspots.get(0).className());
	}

	private boolean hasType(List<ArchitecturalHotspot> hotspots, String type) {
		return hotspots.stream().anyMatch(hotspot -> hotspot.type().equals(type));
	}
}
