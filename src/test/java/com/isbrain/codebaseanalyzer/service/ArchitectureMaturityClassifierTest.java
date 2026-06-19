package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureMaturity;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.PackageAnalysis;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectureMaturityClassifierTest {

	private final ArchitectureMaturityClassifier classifier = new ArchitectureMaturityClassifier();

	@Test
	void classifiesPetClinicSizedProjectAsEarlyStage() {
		var classes = List.of(
				classOf("OwnerController", ComponentType.CONTROLLER, List.of("OwnerRepository")),
				classOf("VisitController", ComponentType.CONTROLLER, List.of("VisitRepository")),
				classOf("OwnerRepository", ComponentType.REPOSITORY, List.of()),
				classOf("VisitRepository", ComponentType.REPOSITORY, List.of()),
				classOf("Owner", ComponentType.ENTITY, List.of()),
				classOf("Visit", ComponentType.ENTITY, List.of()),
				classOf("Pet", ComponentType.ENTITY, List.of()),
				classOf("Vet", ComponentType.ENTITY, List.of()),
				classOf("PetClinicApplication", ComponentType.CONFIGURATION, List.of())
		);

		var maturity = classifier.classify(classes, packages(4));

		assertEquals(ArchitectureMaturity.EARLY_STAGE, maturity);
	}

	@Test
	void classifiesVerySmallProjectAsPrototype() {
		var maturity = classifier.classify(
				List.of(classOf("HealthController", ComponentType.CONTROLLER, List.of())),
				packages(1)
		);

		assertEquals(ArchitectureMaturity.PROTOTYPE, maturity);
	}

	@Test
	void classifiesLargerProjectAsGrowing() {
		var classes = IntStream.range(0, 35)
				.mapToObj(i -> classOf("Service" + i, ComponentType.SERVICE, List.of("Dependency" + i)))
				.toList();

		var maturity = classifier.classify(classes, packages(10));

		assertEquals(ArchitectureMaturity.GROWING, maturity);
	}

	private List<PackageAnalysis> packages(int count) {
		return IntStream.range(0, count)
				.mapToObj(i -> new PackageAnalysis("com.example.p" + i, 1, Map.of()))
				.toList();
	}

	private ClassAnalysis classOf(String className, ComponentType componentType, List<String> dependencies) {
		return new ClassAnalysis(
				className,
				"com.example",
				List.of(),
				List.of(),
				dependencies,
				dependencies,
				componentType,
				null,
				List.of(),
				dependencies.size()
		);
	}
}
