package com.isbrain.codebaseanalyzer.service;

import com.isbrain.codebaseanalyzer.model.ArchitecturalHotspot;
import com.isbrain.codebaseanalyzer.model.ClassMetrics;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ArchitectureHotspotAnalyzer {

	private static final int HIGH_DEPENDENCY_THRESHOLD = 6;
	private static final int LARGE_CLASS_LINE_THRESHOLD = 500;
	private static final int LARGE_PUBLIC_API_THRESHOLD = 15;
	private static final double DEPENDENCY_DENSITY_THRESHOLD = 1.0;
	private static final int MASSIVE_CONSTRUCTOR_THRESHOLD = 7;

	public List<ArchitecturalHotspot> analyze(List<ClassMetrics> classMetrics) {
		List<ArchitecturalHotspot> hotspots = new ArrayList<>();

		for (ClassMetrics metrics : classMetrics) {
			detectHighCoupling(metrics, hotspots);
			detectLargeClass(metrics, hotspots);
			detectLargePublicApi(metrics, hotspots);
			detectHighDependencyDensity(metrics, hotspots);
			detectMassiveConstructor(metrics, hotspots);
		}

		hotspots.sort(Comparator.comparingInt(ArchitecturalHotspot::riskScore).reversed());
		return hotspots;
	}

	private void detectHighCoupling(ClassMetrics metrics, List<ArchitecturalHotspot> hotspots) {
		if (metrics.dependencyCount() >= HIGH_DEPENDENCY_THRESHOLD) {
			hotspots.add(new ArchitecturalHotspot(
					metrics.className(),
					"HIGH_COUPLING",
					riskScore(metrics.dependencyCount(), HIGH_DEPENDENCY_THRESHOLD, 10),
					"%s depends on %d collaborators which may indicate orchestration overload or SRP concerns.".formatted(
							metrics.className(), metrics.dependencyCount())
			));
		}
	}

	private void detectLargeClass(ClassMetrics metrics, List<ArchitecturalHotspot> hotspots) {
		if (metrics.lineCount() >= LARGE_CLASS_LINE_THRESHOLD) {
			hotspots.add(new ArchitecturalHotspot(
					metrics.className(),
					"LARGE_CLASS",
					riskScore(metrics.lineCount(), LARGE_CLASS_LINE_THRESHOLD, 1000),
					"%s contains %d lines. Large classes are difficult to maintain and test.".formatted(
							metrics.className(), metrics.lineCount())
			));
		}
	}

	private void detectLargePublicApi(ClassMetrics metrics, List<ArchitecturalHotspot> hotspots) {
		if (metrics.publicMethodCount() >= LARGE_PUBLIC_API_THRESHOLD) {
			hotspots.add(new ArchitecturalHotspot(
					metrics.className(),
					"LARGE_PUBLIC_API",
					riskScore(metrics.publicMethodCount(), LARGE_PUBLIC_API_THRESHOLD, 30),
					"%s exposes %d public methods. Large public APIs increase maintenance costs and often indicate multiple responsibilities.".formatted(
							metrics.className(), metrics.publicMethodCount())
			));
		}
	}

	private void detectHighDependencyDensity(ClassMetrics metrics, List<ArchitecturalHotspot> hotspots) {
		if (metrics.publicMethodCount() == 0 || metrics.dependencyCount() == 0) {
			return;
		}
		double density = (double) metrics.dependencyCount() / metrics.publicMethodCount();
		if (density > DEPENDENCY_DENSITY_THRESHOLD) {
			hotspots.add(new ArchitecturalHotspot(
					metrics.className(),
					"HIGH_DEPENDENCY_DENSITY",
					Math.min(10, (int) Math.round(density * 3)),
					"%s has %.1f dependencies per public method (%d dependencies, %d public methods). This class may be coordinating too many collaborators for its exposed behavior.".formatted(
							metrics.className(), density, metrics.dependencyCount(), metrics.publicMethodCount())
			));
		}
	}

	private void detectMassiveConstructor(ClassMetrics metrics, List<ArchitecturalHotspot> hotspots) {
		if (metrics.constructorParameterCount() >= MASSIVE_CONSTRUCTOR_THRESHOLD) {
			hotspots.add(new ArchitecturalHotspot(
					metrics.className(),
					"MASSIVE_CONSTRUCTOR",
					riskScore(metrics.constructorParameterCount(), MASSIVE_CONSTRUCTOR_THRESHOLD, 15),
					"%s constructor contains %d parameters. Large constructors often indicate responsibility creep.".formatted(
							metrics.className(), metrics.constructorParameterCount())
			));
		}
	}

	private int riskScore(int actual, int threshold, int maxExpected) {
		double ratio = (double) (actual - threshold) / (maxExpected - threshold);
		return Math.max(1, Math.min(10, (int) Math.round(ratio * 10) + 5));
	}
}
