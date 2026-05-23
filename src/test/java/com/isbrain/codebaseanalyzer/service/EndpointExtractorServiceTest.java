package com.isbrain.codebaseanalyzer.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndpointExtractorServiceTest {

	private EndpointExtractorService extractor;
	private JavaParser javaParser;

	@BeforeEach
	void setUp() {
		extractor = new EndpointExtractorService();
		javaParser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
	}

	private TypeDeclaration<?> parseType(String fixture) throws IOException {
		var result = javaParser.parse(Path.of("src/test/resources/fixtures/" + fixture));
		var cu = result.getResult().orElseThrow();
		return cu.findFirst(TypeDeclaration.class).orElseThrow();
	}

	@Nested
	class HttpMethodMappings {

		@Test
		void extractsGetMapping() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertTrue(endpoints.stream().anyMatch(e ->
					e.httpMethod().equals("GET") && e.methodName().equals("getItems")));
		}

		@Test
		void extractsPostMapping() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertTrue(endpoints.stream().anyMatch(e ->
					e.httpMethod().equals("POST") && e.methodName().equals("createItem")));
		}

		@Test
		void extractsPutMapping() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertTrue(endpoints.stream().anyMatch(e ->
					e.httpMethod().equals("PUT") && e.methodName().equals("updateItem")));
		}

		@Test
		void extractsDeleteMapping() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertTrue(endpoints.stream().anyMatch(e ->
					e.httpMethod().equals("DELETE") && e.methodName().equals("deleteItem")));
		}

		@Test
		void extractsPatchMapping() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertTrue(endpoints.stream().anyMatch(e ->
					e.httpMethod().equals("PATCH") && e.methodName().equals("patchItem")));
		}

		@Test
		void extractsAllFiveEndpoints() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertEquals(5, endpoints.size());
		}
	}

	@Nested
	class PathHandling {

		@Test
		void combinesBasePathWithMethodPath() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			var getEndpoint = endpoints.stream()
					.filter(e -> e.methodName().equals("getItems"))
					.findFirst().orElseThrow();

			assertEquals("/api/items", getEndpoint.endpointPath());
		}

		@Test
		void worksWithoutBasePath() throws IOException {
			var type = parseType("NoBasePath.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertEquals(1, endpoints.size());
			assertEquals("/health", endpoints.get(0).endpointPath());
		}

		@Test
		void setsBasePathOnEndpoint() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertTrue(endpoints.stream().allMatch(e -> e.basePath().equals("/api")));
		}

		@Test
		void handlesPathVariables() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			var deleteEndpoint = endpoints.stream()
					.filter(e -> e.methodName().equals("deleteItem"))
					.findFirst().orElseThrow();

			assertEquals("/api/items/{id}", deleteEndpoint.endpointPath());
		}
	}

	@Nested
	class RequestMappingAnnotation {

		@Test
		void extractsRequestMappingWithValueAndMethod() throws IOException {
			var type = parseType("RequestMappingController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			var getData = endpoints.stream()
					.filter(e -> e.methodName().equals("getData"))
					.findFirst().orElseThrow();

			assertEquals("GET", getData.httpMethod());
			assertEquals("/legacy/data", getData.endpointPath());
		}

		@Test
		void extractsRequestMappingWithPathAttribute() throws IOException {
			var type = parseType("RequestMappingController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			var postInfo = endpoints.stream()
					.filter(e -> e.methodName().equals("postInfo"))
					.findFirst().orElseThrow();

			assertEquals("POST", postInfo.httpMethod());
			assertEquals("/legacy/info", postInfo.endpointPath());
		}
	}

	@Nested
	class ControllerName {

		@Test
		void setsControllerNameFromTypeDeclaration() throws IOException {
			var type = parseType("AllHttpMethodsController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertTrue(endpoints.stream().allMatch(e ->
					e.controllerName().equals("AllHttpMethodsController")));
		}
	}

	@Nested
	class NonControllerTypes {

		@Test
		void returnsEmptyForService() throws IOException {
			var type = parseType("SampleService.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.SERVICE);

			assertTrue(endpoints.isEmpty());
		}

		@Test
		void returnsEmptyForRepository() throws IOException {
			var type = parseType("SampleRepository.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REPOSITORY);

			assertTrue(endpoints.isEmpty());
		}

		@Test
		void returnsEmptyForEntity() throws IOException {
			var type = parseType("SampleEntity.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.ENTITY);

			assertTrue(endpoints.isEmpty());
		}

		@Test
		void returnsEmptyForComponent() throws IOException {
			var type = parseType("PlainClass.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.COMPONENT);

			assertTrue(endpoints.isEmpty());
		}

		@Test
		void returnsEmptyForUnknown() throws IOException {
			var type = parseType("PlainClass.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.UNKNOWN);

			assertTrue(endpoints.isEmpty());
		}
	}

	@Nested
	class EdgeCases {

		@Test
		void controllerWithNoMappedMethods() throws IOException {
			var type = parseType("NoEndpointMethods.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.REST_CONTROLLER);

			assertTrue(endpoints.isEmpty());
		}

		@Test
		void worksWithPlainControllerType() throws IOException {
			var type = parseType("SampleController.java");
			var endpoints = extractor.extractEndpoints(type, ComponentType.CONTROLLER);

			assertEquals(2, endpoints.size());
		}
	}
}
