package com.labo.anapath.common.storage;

import com.labo.anapath.common.exception.InvalidOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "gif", "webp", "docx", "xlsx", "doc", "xls"
    );

    private final Path basePath;

    // Même valeur par défaut que les deux autres services de stockage
    // (testorder.FileStorageService et hr.FileStorageServiceImpl) : avec « ./storage »
    // ce service écrivait et relisait ailleurs que les autres si `app.storage.path`
    // venait à manquer — un fichier téléversé d'un côté aurait été introuvable de
    // l'autre, et c'est ce service qui sert /api/v1/files/**.
    public FileStorageServiceImpl(@Value("${app.storage.path:/tmp/labo/storage}") String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file, String directory) {
        try {
            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
                throw new InvalidOperationException("Type de fichier non autorisé : " + ext);
            }
            String filename = UUID.randomUUID() + "." + ext.toLowerCase();
            Path targetDir = basePath.resolve(directory).normalize();
            if (!targetDir.startsWith(basePath)) {
                throw new InvalidOperationException("Répertoire cible invalide");
            }
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return directory + "/" + filename;
        } catch (IOException e) {
            throw new InvalidOperationException("Erreur lors du stockage du fichier: " + e.getMessage());
        }
    }

    @Override
    public Path resolve(String relativePath) {
        return basePath.resolve(relativePath).normalize();
    }

    @Override
    public void delete(String relativePath) {
        try {
            Path resolved = basePath.resolve(relativePath).normalize();
            if (!resolved.startsWith(basePath)) {
                log.warn("Tentative de suppression hors basePath: {}", relativePath);
                return;
            }
            Files.deleteIfExists(resolved);
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier {}: {}", relativePath, e.getMessage());
        }
    }
}
