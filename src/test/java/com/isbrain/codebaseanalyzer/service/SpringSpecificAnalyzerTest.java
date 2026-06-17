package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import com.isbrain.codebaseanalyzer.model.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSpecificAnalyzerTest {

	private final SpringSpecificAnalyzer analyzer = new SpringSpecificAnalyzer();

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
}
