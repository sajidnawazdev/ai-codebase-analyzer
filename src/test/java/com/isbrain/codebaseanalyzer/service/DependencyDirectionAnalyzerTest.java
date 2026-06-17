package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyDirectionAnalyzerTest {

	private DependencyDirectionAnalyzer analyzer;

	@BeforeEach
	void setUp() {
		analyzer = new DependencyDirectionAnalyzer();
	}

	@Test
	void allowsControllerToServiceAndRepository() {
		var controller = classOf("OwnerController", ComponentType.CONTROLLER, List.of("OwnerService", "OwnerRepository"));
		var service = classOf("OwnerService", ComponentType.SERVICE, List.of());
		var repository = classOf("OwnerRepository", ComponentType.REPOSITORY, List.of());

		var findings = analyzer.analyze(List.of(controller, service, repository));

		assertTrue(findings.isEmpty());
	}

	@Test
	void allowsServiceToRepositoryAndService() {
		var service = classOf("VisitService", ComponentType.SERVICE, List.of("OwnerRepository", "NotificationService"));
		var repository = classOf("OwnerRepository", ComponentType.REPOSITORY, List.of());
		var dependencyService = classOf("NotificationService", ComponentType.SERVICE, List.of());

		var findings = analyzer.analyze(List.of(service, repository, dependencyService));

		assertTrue(findings.isEmpty());
	}

	@Test
	void allowsRepositoryToEntity() {
		var repository = classOf("OwnerRepository", ComponentType.REPOSITORY, List.of("Owner"));
		var entity = classOf("Owner", ComponentType.ENTITY, List.of());

		var findings = analyzer.analyze(List.of(repository, entity));

		assertTrue(findings.isEmpty());
	}

	@Test
	void flagsEntityDependingOnService() {
		var entity = classOf("OwnerEntity", ComponentType.ENTITY, List.of("OwnerService"));
		var service = classOf("OwnerService", ComponentType.SERVICE, List.of());

		var findings = analyzer.analyze(List.of(entity, service));

		assertEquals(1, findings.size());
		assertEquals("OwnerEntity", findings.get(0).className());
		assertEquals("ENTITY_DEPENDS_ON_SERVICE", findings.get(0).type());
		assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
	}

	@Test
	void flagsRepositoryDependingOnService() {
		var repository = classOf("OwnerRepository", ComponentType.REPOSITORY, List.of("OwnerService"));
		var service = classOf("OwnerService", ComponentType.SERVICE, List.of());

		var findings = analyzer.analyze(List.of(repository, service));

		assertEquals(1, findings.size());
		assertEquals("REPOSITORY_DEPENDS_ON_SERVICE", findings.get(0).type());
		assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
	}

	@Test
	void flagsConfigurationDependingOnController() {
		var configuration = classOf("WebConfig", ComponentType.CONFIGURATION, List.of("OwnerController"));
		var controller = classOf("OwnerController", ComponentType.CONTROLLER, List.of());

		var findings = analyzer.analyze(List.of(configuration, controller));

		assertEquals(1, findings.size());
		assertEquals("CONFIGURATION_DEPENDS_ON_CONTROLLER", findings.get(0).type());
		assertEquals(FindingSeverity.MEDIUM, findings.get(0).severity());
	}

	@Test
	void ignoresExternalDependencies() {
		var entity = classOf("OwnerEntity", ComponentType.ENTITY, List.of("ExternalService"));

		var findings = analyzer.analyze(List.of(entity));

		assertTrue(findings.isEmpty());
	}

	private ClassAnalysis classOf(String className, ComponentType componentType, List<String> dependencies) {
		return new ClassAnalysis(
				className,
				"com.app",
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
