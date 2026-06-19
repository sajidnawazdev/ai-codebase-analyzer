package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSpecificAnalyzerTest {

	private final SpringSpecificAnalyzer analyzer = new SpringSpecificAnalyzer();

	@TempDir
	Path tempDir;

	@Test
	void detectsListEndpointWithoutPagination() {
		var endpoint = new EndpointAnalysis(
				"OwnerController",
				"/owners",
				"GET",
				"/owners",
				"processFindForm",
				"String",
				List.of(),
				List.of(),
				true
		);

		var observations = analyzer.detectListEndpointsWithoutPagination(List.of(endpoint));

		assertEquals(1, observations.size());
		var observation = observations.get(0);
		assertEquals("LIST_ENDPOINT_WITHOUT_PAGINATION", observation.type());
		assertEquals("OwnerController", observation.className());
		assertEquals("GET /owners may return multiple owners without explicit pagination.", observation.description());
		assertEquals(FindingSeverity.LOW, observation.severity());
		assertEquals(FindingConfidence.POSSIBLE, observation.findingConfidence());
	}

	@Test
	void ignoresEndpointWithPageableParameter() {
		var endpoint = new EndpointAnalysis(
				"OwnerController",
				"/owners",
				"GET",
				"/owners",
				"listOwners",
				"Page<Owner>",
				List.of("Pageable"),
				List.of("pageable")
		);

		var observations = analyzer.detectListEndpointsWithoutPagination(List.of(endpoint));

		assertTrue(observations.isEmpty());
	}

	@Test
	void ignoresSingleResourceEndpoint() {
		var endpoint = new EndpointAnalysis("OwnerController", "/owners", "GET", "/owners/{ownerId}", "showOwner");

		var observations = analyzer.detectListEndpointsWithoutPagination(List.of(endpoint));

		assertTrue(observations.isEmpty());
	}

	@Test
	void ignoresCrashControllerOupsActionEndpoint() {
		var endpoint = new EndpointAnalysis(
				"CrashController",
				"",
				"GET",
				"/oups",
				"triggerException",
				"String",
				List.of(),
				List.of(),
				false
		);

		var observations = analyzer.detectListEndpointsWithoutPagination(List.of(endpoint));

		assertTrue(observations.isEmpty());
	}

	@Test
	void ignoresPluralPathWithoutCollectionSignal() {
		var endpoint = new EndpointAnalysis(
				"ActionController",
				"",
				"GET",
				"/oups",
				"oups",
				"String",
				List.of(),
				List.of(),
				false
		);

		var observations = analyzer.detectListEndpointsWithoutPagination(List.of(endpoint));

		assertTrue(observations.isEmpty());
	}

	@Test
	void ignoresInfrastructureRoutes() {
		var endpoints = List.of(
				new EndpointAnalysis("HealthController", "", "GET", "/health", "health", "String", List.of(), List.of(), true),
				new EndpointAnalysis("ActuatorController", "", "GET", "/actuator/metrics", "metrics", "List<String>", List.of(), List.of(), true),
				new EndpointAnalysis("LoginController", "", "GET", "/login", "login", "String", List.of(), List.of(), true),
				new EndpointAnalysis("HomeController", "", "GET", "/", "home", "String", List.of(), List.of(), true)
		);

		var observations = analyzer.detectListEndpointsWithoutPagination(endpoints);

		assertTrue(observations.isEmpty());
	}

	@Test
	void detectsAutowiredFieldInjection() throws Exception {
		Path source = writeJavaFile("FieldInjectedService.java", """
				package com.example;

				import org.springframework.beans.factory.annotation.Autowired;
				import org.springframework.stereotype.Service;

				@Service
				class FieldInjectedService {
					@Autowired
					private OwnerRepository ownerRepository;
				}

				interface OwnerRepository {
				}
				""");

		var observations = analyzer.detectBestPracticeIssues(List.of(source));

		assertEquals(1, observations.size());
		var observation = observations.get(0);
		assertEquals("FIELD_INJECTION", observation.type());
		assertEquals("FieldInjectedService", observation.className());
		assertEquals("Field injection reduces testability. Prefer constructor injection.", observation.description());
		assertEquals(FindingSeverity.LOW, observation.severity());
		assertEquals(FindingConfidence.POSSIBLE, observation.findingConfidence());
	}

	@Test
	void detectsServiceWriteMethodWithoutTransactional() throws Exception {
		Path source = writeJavaFile("OwnerService.java", """
				package com.example;

				import org.springframework.stereotype.Service;

				@Service
				class OwnerService {
					private final OwnerRepository ownerRepository;

					OwnerService(OwnerRepository ownerRepository) {
						this.ownerRepository = ownerRepository;
					}

					void create(Owner owner) {
						ownerRepository.save(owner);
					}
				}

				interface OwnerRepository {
					void save(Owner owner);
				}

				class Owner {
				}
				""");

		var observations = analyzer.detectBestPracticeIssues(List.of(source));

		assertEquals(1, observations.size());
		var observation = observations.get(0);
		assertEquals("MISSING_TRANSACTIONAL_ON_WRITE_METHOD", observation.type());
		assertEquals("OwnerService", observation.className());
		assertEquals("Write operation has unclear transaction boundary.", observation.description());
		assertEquals(FindingSeverity.MEDIUM, observation.severity());
		assertEquals(FindingConfidence.LIKELY, observation.findingConfidence());
	}

	@Test
	void ignoresTransactionalWriteMethodsAndControllerWrites() throws Exception {
		Path source = writeJavaFile("TransactionalCases.java", """
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.stereotype.Service;
				import org.springframework.transaction.annotation.Transactional;

				@Service
				class OwnerService {
					private final OwnerRepository ownerRepository;

					OwnerService(OwnerRepository ownerRepository) {
						this.ownerRepository = ownerRepository;
					}

					@Transactional
					void create(Owner owner) {
						ownerRepository.save(owner);
					}
				}

				@Controller
				class OwnerController {
					void create(OwnerRepository ownerRepository, Owner owner) {
						ownerRepository.save(owner);
					}
				}

				interface OwnerRepository {
					void save(Owner owner);
				}

				class Owner {
				}
				""");

		var observations = analyzer.detectBestPracticeIssues(List.of(source));

		assertTrue(observations.stream()
				.noneMatch(observation -> observation.type().equals("MISSING_TRANSACTIONAL_ON_WRITE_METHOD")));
	}

	@Test
	void detectsRepositoryMethodExplosionAtTenCustomMethods() throws Exception {
		Path source = writeJavaFile("OwnerRepository.java", """
				package com.example;

				import org.springframework.data.jpa.repository.JpaRepository;

				interface OwnerRepository extends JpaRepository<Owner, Integer> {
					Owner findOne();
					Owner findTwo();
					Owner findThree();
					Owner findFour();
					Owner findFive();
					Owner findSix();
					Owner findSeven();
					Owner findEight();
					Owner findNine();
					Owner findTen();
				}

				class Owner {
				}
				""");

		var observations = analyzer.detectBestPracticeIssues(List.of(source));

		assertEquals(1, observations.size());
		var observation = observations.get(0);
		assertEquals("REPOSITORY_METHOD_EXPLOSION", observation.type());
		assertEquals("OwnerRepository", observation.className());
		assertEquals("Repository may be accumulating query responsibility.", observation.description());
		assertEquals(FindingSeverity.LOW, observation.severity());
		assertEquals(FindingConfidence.POSSIBLE, observation.findingConfidence());
	}

	private Path writeJavaFile(String fileName, String source) throws Exception {
		Path path = tempDir.resolve(fileName);
		Files.writeString(path, source);
		return path;
	}
}
