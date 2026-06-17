package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.AnalysisSummary;
import com.isbrain.codebaseanalyzer.model.ArchitectureStyle;
import com.isbrain.codebaseanalyzer.model.EndpointAnalysis;
import com.isbrain.codebaseanalyzer.model.FindingConfidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectureStyleClassifierTest {

	private final ArchitectureStyleClassifier classifier = new ArchitectureStyleClassifier();

	@Test
	void detectsSimpleCrudForPetClinicLikeStructure() {
		var summary = new AnalysisSummary(4, 1, 4, 0, 1, 4, 0, 0);
		var endpoints = List.of(new EndpointAnalysis("OwnerController", "/owners", "GET", "/owners", "processFindForm"));

		var assessment = classifier.assess(summary, List.of(), endpoints, List.of());

		assertEquals(ArchitectureStyle.SIMPLE_CRUD, assessment.primary());
		assertEquals(FindingConfidence.CONFIRMED, assessment.confidence());
		assertEquals(4, assessment.reasoning().size());
	}

	@Test
	void detectsLayeredWhenServiceLayerIsProminent() {
		var summary = new AnalysisSummary(3, 5, 3, 0, 1, 3, 0, 0);
		var endpoints = List.of(new EndpointAnalysis("OrderController", "/orders", "GET", "/orders", "listOrders"));

		var style = classifier.classify(summary, List.of(), endpoints, List.of());

		assertEquals(ArchitectureStyle.LAYERED, style);
	}
}
