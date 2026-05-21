package com.isbrain.codebaseanalyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isbrain.codebaseanalyzer.model.AiAnalysisReport;
import com.isbrain.codebaseanalyzer.model.ProjectAnalysisResult;
import com.isbrain.codebaseanalyzer.prompt.PromptBuilderService;
import lombok.RequiredArgsConstructor;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

	private final ChatClient chatClient;
	private final PromptBuilderService promptBuilderService;
	private final ObjectMapper objectMapper;

	public AiAnalysisReport analyseWithAi(ProjectAnalysisResult result) {
		String prompt = promptBuilderService.buildArchitecturePrompt(result, result.violations());

		String aiResponse = chatClient
				.prompt(prompt)
				.call()
				.content();

		// fail fast if the chat client returned nothing
		Objects.requireNonNull(aiResponse, "AI response from chatClient was null");
		if (aiResponse.isBlank()) {
			throw new IllegalStateException("AI response from chatClient was empty");
		}

		return parseAiResponse(aiResponse);
	}

	private AiAnalysisReport parseAiResponse(String aiResponse) {
		try {
			String json = aiResponse.strip();
			if (json.startsWith("```")) {
				json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
			}
			return objectMapper.readValue(json, AiAnalysisReport.class);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to parse AI response into AiAnalysisReport", e);
		}
	}
}
