package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureObservation;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ClassMetrics;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureRiskScorerTest {

	private ArchitectureRiskScorer scorer;

	@BeforeEach
	void setUp() {
		scorer = new ArchitectureRiskScorer();
	}

	@Test
	void scoresLayeringObservationsAtFivePointsEach() {
		var score = scorer.score(
				List.of(
						observation("LAYERING", "OwnerController"),
						observation("LAYERING", "VisitController")
				),
				List.of(),
				List.of()
		);

		assertEquals(10, score.layeringRisk());
	}

	@Test
	void scoresCircularDependenciesAsModularityRisk() {
		var score = scorer.score(
				List.of(observation("CIRCULAR_DEPENDENCY", "OrderService")),
				List.of(),
				List.of()
		);

		assertEquals(25, score.modularityRisk());
	}

	@Test
	void scoresDirectPackageCyclesAsModularityRisk() {
		var a = classOf("A", "com.app.a", List.of("B"));
		var b = classOf("B", "com.app.b", List.of("A"));

		var score = scorer.score(List.of(), List.of(), List.of(a, b));

		assertEquals(20, score.modularityRisk());
	}

	@Test
	void scoresCouplingRiskFromDependencyCounts() {
		var score = scorer.score(
				List.of(),
				List.of(
						metrics("Coordinator", 6, 5, 100),
						metrics("MegaCoordinator", 10, 5, 100)
				),
				List.of()
		);

		assertEquals(15, score.couplingRisk());
	}

	@Test
	void scoresMaintainabilityRiskFromLargeClassesAndPublicApis() {
		var score = scorer.score(
				List.of(),
				List.of(
						metrics("LargeService", 3, 15, 500),
						metrics("HugeService", 3, 25, 1000)
				),
				List.of()
		);

		assertEquals(30, score.maintainabilityRisk());
	}

	@Test
	void scoresScalabilityRiskFromPackageFanOutAndHighCoupling() {
		var source = classOf("Source", "com.app.source", List.of("A", "B", "C", "D", "E", "F"));
		var a = classOf("A", "com.app.a", List.of());
		var b = classOf("B", "com.app.b", List.of());
		var c = classOf("C", "com.app.c", List.of());
		var d = classOf("D", "com.app.d", List.of());
		var e = classOf("E", "com.app.e", List.of());
		var f = classOf("F", "com.app.f", List.of());

		var score = scorer.score(
				List.of(),
				List.of(metrics("HighCouplingService", 9, 5, 100)),
				List.of(source, a, b, c, d, e, f)
		);

		assertEquals(10, score.scalabilityRisk());
	}

	@Test
	void capsRiskScoresAtOneHundred() {
		List<ArchitectureObservation> observations = java.util.stream.IntStream.range(0, 30)
				.mapToObj(i -> observation("LAYERING", "Controller" + i))
				.toList();

		var score = scorer.score(observations, List.of(), List.of());

		assertEquals(100, score.layeringRisk());
	}

	private ArchitectureObservation observation(String type, String className) {
		return new ArchitectureObservation(type, className, "description", 60, true);
	}

	private ClassMetrics metrics(String className, int dependencies, int publicMethods, int lines) {
		return new ClassMetrics(
				className,
				"com.app",
				ComponentType.SERVICE,
				publicMethods,
				publicMethods,
				dependencies,
				dependencies,
				dependencies,
				lines
		);
	}

	private ClassAnalysis classOf(String name, String packageName, List<String> dependencies) {
		return new ClassAnalysis(
				name,
				packageName,
				List.of(),
				List.of(),
				dependencies,
				dependencies,
				ComponentType.SERVICE,
				null,
				List.of(),
				dependencies.size()
		);
	}
}
