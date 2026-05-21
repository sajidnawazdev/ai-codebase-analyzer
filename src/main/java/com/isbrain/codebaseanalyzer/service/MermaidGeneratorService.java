package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.ComponentType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MermaidGeneratorService {

	private static final Map<ComponentType, String> STYLE_MAP = Map.of(
			ComponentType.REST_CONTROLLER, "fill:#4CAF50,color:#fff",
			ComponentType.CONTROLLER, "fill:#4CAF50,color:#fff",
			ComponentType.SERVICE, "fill:#2196F3,color:#fff",
			ComponentType.REPOSITORY, "fill:#FF9800,color:#fff",
			ComponentType.CONFIGURATION, "fill:#9C27B0,color:#fff",
			ComponentType.COMPONENT, "fill:#607D8B,color:#fff",
			ComponentType.ENTITY, "fill:#795548,color:#fff",
			ComponentType.CONTROLLER_ADVICE, "fill:#00BCD4,color:#fff",
			ComponentType.REST_CONTROLLER_ADVICE, "fill:#00BCD4,color:#fff"
	);

	public String generateDiagram(List<ClassAnalysis> classes) {
		StringBuilder sb = new StringBuilder("graph TD\n");

		Set<String> styledNodes = new LinkedHashSet<>();

		for (ClassAnalysis clazz : classes) {
			for (String dependency : clazz.dependencies()) {
				sb.append("  %s --> %s\n".formatted(clazz.className(), dependency));
				styledNodes.add(clazz.className());
				styledNodes.add(dependency);
			}
		}

		Map<String, ComponentType> typeByName = new java.util.HashMap<>();
		for (ClassAnalysis clazz : classes) {
			typeByName.put(clazz.className(), clazz.componentType());
		}

		for (String nodeName : styledNodes) {
			ComponentType type = typeByName.getOrDefault(nodeName, ComponentType.UNKNOWN);
			String style = STYLE_MAP.get(type);
			if (style != null) {
				sb.append("  style %s %s\n".formatted(nodeName, style));
			}
		}

		return sb.toString();
	}
}
