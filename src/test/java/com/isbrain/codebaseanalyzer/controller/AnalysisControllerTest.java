package com.isbrain.codebaseanalyzer.controller;

import com.isbrain.codebaseanalyzer.model.*;
import com.isbrain.codebaseanalyzer.service.AiAnalysisService;
import com.isbrain.codebaseanalyzer.service.GitCloneService;
import com.isbrain.codebaseanalyzer.service.ProjectScannerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisController.class)
@Import(AnalysisControllerTest.TestSecurityConfig.class)
class AnalysisControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectScannerService projectScannerService;

	@MockitoBean
	private AiAnalysisService aiAnalysisService;

	@MockitoBean
	private GitCloneService gitCloneService;

	private ProjectAnalysisResult sampleResult() {
		return new ProjectAnalysisResult(
				new AnalysisSummary(1, 2, 1, 0, 1, 1, 0, 0),
				List.of("Controller -> Service", "Service -> Repository"),
				List.of(new EndpointAnalysis("UserController", "/users", "GET", "/users", "getAll")),
				List.of(new PackageAnalysis("com.app.controller", 1, Map.of(ComponentType.REST_CONTROLLER, 1L))),
				List.of(),
				List.of("No layer violations detected"),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				List.of("UserService (SERVICE) — coupling score: 3 (outgoing: 1, incoming: 2)"),
				"graph TD\n  Controller --> Service\n"
		);
	}

	static class TestSecurityConfig {
		@Bean
		public JwtDecoder jwtDecoder() {
			return mock(JwtDecoder.class);
		}
	}

	@Test
	@WithMockUser(roles = "USER")
	void analyseReturnsProjectAnalysisResult() throws Exception {
		when(projectScannerService.analyseProject(any())).thenReturn(sampleResult());

		mockMvc.perform(post("/analyse")
						.param("projectPath", "/some/path"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.controllers").value(1))
				.andExpect(jsonPath("$.summary.services").value(2))
				.andExpect(jsonPath("$.summary.repositories").value(1))
				.andExpect(jsonPath("$.relationships").isArray())
				.andExpect(jsonPath("$.relationships[0]").value("Controller -> Service"))
				.andExpect(jsonPath("$.endpoints").isArray())
				.andExpect(jsonPath("$.endpoints[0].httpMethod").value("GET"))
				.andExpect(jsonPath("$.endpoints[0].controllerName").value("UserController"))
				.andExpect(jsonPath("$.packages").isArray())
				.andExpect(jsonPath("$.violations").isArray())
				.andExpect(jsonPath("$.circularDependencies").isArray())
				.andExpect(jsonPath("$.godClasses").isArray())
				.andExpect(jsonPath("$.emptyControllers").isArray())
				.andExpect(jsonPath("$.orphanServices").isArray())
				.andExpect(jsonPath("$.fatControllers").isArray())
				.andExpect(jsonPath("$.couplingRanking").isArray())
				.andExpect(jsonPath("$.mermaidDiagram").isString());
	}

	@Test
	@WithMockUser(roles = "USER")
	void analyseFailsWithoutProjectPathOrRepoUrl() {
		assertThrows(Exception.class, () ->
				mockMvc.perform(post("/analyse")));
	}

	@Test
	@WithMockUser(roles = "USER")
	void analyseWithAiReturnsFullResponse() throws Exception {
		when(projectScannerService.analyseProject(any())).thenReturn(sampleResult());
		when(aiAnalysisService.analyseWithAi(any())).thenReturn(new AiAnalysisReport(
				"Well structured project", 8,
				List.of("Clean layering"), List.of(), List.of("Add more tests"),
				Map.of("layering", 8, "modularity", 7),
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
		));

		mockMvc.perform(post("/analyse/ai")
						.param("projectPath", "/some/path"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aiReport.overallAssessment").value("Well structured project"))
				.andExpect(jsonPath("$.aiReport.architectureScore").value(8))
				.andExpect(jsonPath("$.aiReport.strengths[0]").value("Clean layering"))
				.andExpect(jsonPath("$.mermaidDiagram").isString())
				.andExpect(jsonPath("$.violations").isArray())
				.andExpect(jsonPath("$.rawAnalysis").isNotEmpty());
	}
}
