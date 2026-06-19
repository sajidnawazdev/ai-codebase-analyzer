package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitectureMaturity;
import com.isbrain.codebaseanalyzer.model.ClassAnalysis;
import com.isbrain.codebaseanalyzer.model.PackageAnalysis;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArchitectureMaturityClassifier {

	public ArchitectureMaturity classify(List<ClassAnalysis> classes, List<PackageAnalysis> packages) {
		int classCount = classes.size();
		int packageCount = packages.size();
		int controllerCount = countClassesByComponentName(classes, "CONTROLLER");
		int serviceCount = countClassesByComponentName(classes, "SERVICE");
		int dependencyCount = classes.stream()
				.mapToInt(clazz -> clazz.dependencies().size())
				.sum();

		int maturityScore = 0;
		maturityScore += score(classCount, 8, 30, 80);
		maturityScore += score(packageCount, 3, 8, 18);
		maturityScore += score(controllerCount, 2, 8, 20);
		maturityScore += score(serviceCount, 1, 8, 20);
		maturityScore += score(dependencyCount, 8, 35, 100);

		if (maturityScore >= 13) {
			return ArchitectureMaturity.ENTERPRISE;
		}
		if (maturityScore >= 8) {
			return ArchitectureMaturity.GROWING;
		}
		if (maturityScore >= 3) {
			return ArchitectureMaturity.EARLY_STAGE;
		}
		return ArchitectureMaturity.PROTOTYPE;
	}

	private int countClassesByComponentName(List<ClassAnalysis> classes, String componentName) {
		return (int) classes.stream()
				.filter(clazz -> clazz.componentType().name().contains(componentName))
				.count();
	}

	private int score(int value, int earlyStageThreshold, int growingThreshold, int enterpriseThreshold) {
		if (value >= enterpriseThreshold) {
			return 3;
		}
		if (value >= growingThreshold) {
			return 2;
		}
		if (value >= earlyStageThreshold) {
			return 1;
		}
		return 0;
	}
}
