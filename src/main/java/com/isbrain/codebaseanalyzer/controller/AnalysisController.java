package com.isbrain.codebaseanalyzer.controller;

import com.isbrain.codebaseanalyzer.model.AiAnalysisReport;
import com.isbrain.codebaseanalyzer.model.AnalysisCompletedEvent;
import com.isbrain.codebaseanalyzer.model.FullAnalysisResponse;
import com.isbrain.codebaseanalyzer.model.ProjectAnalysisResult;
import com.isbrain.codebaseanalyzer.service.AiAnalysisService;
import com.isbrain.codebaseanalyzer.service.AnalysisEventPublisher;
import com.isbrain.codebaseanalyzer.service.GitCloneService;
import com.isbrain.codebaseanalyzer.service.ProjectScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/analyse")
@RequiredArgsConstructor
public class AnalysisController {

	private final ProjectScannerService projectScannerService;
	private final AiAnalysisService aiAnalysisService;
	private final GitCloneService gitCloneService;
	private final AnalysisEventPublisher analysisEventPublisher;

	@PostMapping
	public ProjectAnalysisResult analyse(
			@RequestParam(required = false) String projectPath,
			@RequestParam(required = false) String repoUrl) {
		Path clonedDir = null;
		try {
			String scanPath = resolveProjectPath(projectPath, repoUrl);
			clonedDir = repoUrl != null ? Path.of(scanPath) : null;
			return projectScannerService.analyseProject(scanPath);
		} finally {
			gitCloneService.cleanup(clonedDir);
		}
	}

	@PostMapping("/ai")
	public FullAnalysisResponse analyseWithAi(
			@RequestParam(required = false) String projectPath,
			@RequestParam(required = false) String repoUrl) {
		Path clonedDir = null;
		try {
			String scanPath = resolveProjectPath(projectPath, repoUrl);
			clonedDir = repoUrl != null ? Path.of(scanPath) : null;

			ProjectAnalysisResult result = projectScannerService.analyseProject(scanPath);
			AiAnalysisReport aiReport = aiAnalysisService.analyseWithAi(result);

			List<String> allViolations = new ArrayList<>();
			allViolations.addAll(result.violations());
			allViolations.addAll(result.circularDependencies());
			allViolations.addAll(result.godClasses());
			allViolations.addAll(result.emptyControllers());
			allViolations.addAll(result.orphanServices());
			allViolations.addAll(result.fatControllers());

			FullAnalysisResponse response = new FullAnalysisResponse(
					aiReport,
					result.mermaidDiagram(),
					allViolations,
					result
			);

			publishEventAsync(aiReport, allViolations.size(), scanPath);

			return response;
		} finally {
			gitCloneService.cleanup(clonedDir);
		}
	}

	private void publishEventAsync(AiAnalysisReport aiReport,
								   int violationCount, String projectPath) {
		String projectName = Paths.get(projectPath).getFileName().toString();
		AnalysisCompletedEvent event = new AnalysisCompletedEvent(
				projectName,
				projectPath,
				aiReport.architectureScore(),
				aiReport.categoryScores(),
				violationCount,
				LocalDateTime.now()
		);
		CompletableFuture.runAsync(() -> analysisEventPublisher.publish(event))
				.exceptionally(ex -> {
					log.error("Failed to publish analysis event for project: {}", projectName, ex);
					return null;
				});
	}

	private String resolveProjectPath(String projectPath, String repoUrl) {
		if (repoUrl != null && !repoUrl.isBlank()) {
			return gitCloneService.cloneRepo(repoUrl).toString();
		}
		if (projectPath != null && !projectPath.isBlank()) {
			return projectPath;
		}
		throw new IllegalArgumentException("Either projectPath or repoUrl must be provided");
	}
}
