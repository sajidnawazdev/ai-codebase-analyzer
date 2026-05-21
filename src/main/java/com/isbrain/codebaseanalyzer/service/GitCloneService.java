package com.isbrain.codebaseanalyzer.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Service
public class GitCloneService {

	public Path cloneRepo(String repoUrl) {
		try {
			Path tempDir = Files.createTempDirectory("codebase-analyzer-");
			Git.cloneRepository()
					.setURI(repoUrl)
					.setDirectory(tempDir.toFile())
					.setDepth(1)
					.call()
					.close();
			return tempDir;
		} catch (GitAPIException e) {
			throw new RuntimeException("Failed to clone repository: " + repoUrl, e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void cleanup(Path directory) {
		if (directory == null || !Files.exists(directory)) {
			return;
		}
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder())
					.forEach(path -> {
						try {
							Files.delete(path);
						} catch (IOException e) {
							// best effort cleanup
						}
					});
		} catch (IOException e) {
			// best effort cleanup
		}
	}
}
