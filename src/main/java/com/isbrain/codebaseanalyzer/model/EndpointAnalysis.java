package com.isbrain.codebaseanalyzer.model;

import java.util.List;

public record EndpointAnalysis(
		String controllerName,
		String basePath,
		String httpMethod,
		String endpointPath,
		String methodName,
		String returnType,
		List<String> parameterTypes,
		List<String> parameterNames,
		boolean collectionResponseSignal
) {
	public EndpointAnalysis(
			String controllerName,
			String basePath,
			String httpMethod,
			String endpointPath,
			String methodName
	) {
		this(controllerName, basePath, httpMethod, endpointPath, methodName, "", List.of(), List.of(), false);
	}

	public EndpointAnalysis(
			String controllerName,
			String basePath,
			String httpMethod,
			String endpointPath,
			String methodName,
			String returnType,
			List<String> parameterTypes,
			List<String> parameterNames
	) {
		this(controllerName, basePath, httpMethod, endpointPath, methodName, returnType, parameterTypes, parameterNames,
				false);
	}
}
