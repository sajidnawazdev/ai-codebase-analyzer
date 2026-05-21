package com.isbrain.codebaseanalyzer.model;

public record EndpointAnalysis(
		String controllerName,
		String basePath,
		String httpMethod,
		String endpointPath,
		String methodName
) {
}
