package com.smartrecruit.shared.storage;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/** @param rootDirectory dossier des fichiers téléversés ; hors du dossier servi par le web, jamais public */
@Validated
@ConfigurationProperties(prefix = "smartrecruit.storage")
public record StorageProperties(@NotNull Path rootDirectory) {
}
