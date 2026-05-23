package com.isbrain.codebaseanalyzer.service;

import org.springframework.stereotype.Service;

@Service
public class SampleService {

	private final UserRepository userRepository;

	public SampleService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public void doWork() {
	}

	public void doMoreWork() {
	}
}
