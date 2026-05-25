package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.AnalysisCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisEventPublisher {

	private static final String TOPIC = "analysis-completed";

	private final KafkaTemplate<String, AnalysisCompletedEvent> kafkaTemplate;

	public void publish(AnalysisCompletedEvent event) {
		log.info("Publishing analysis event for project: {}", event.projectName());
		kafkaTemplate.send(TOPIC, event.projectName(), event);
	}
}
