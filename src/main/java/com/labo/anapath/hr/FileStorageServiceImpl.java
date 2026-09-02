package com.labo.anapath.hr;

import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.storage.StoredFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service("hrFileStorageServiceImpl")
@Slf4j
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final StoredFiles storedFiles;

    @Value("${app.storage.path:/tmp/labo/storage}")
    private String basePath;

    @Override
    public String store(MultipartFile file, String subDirectory) {
        try {
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String safeFilename = UUID.randomUUID() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path dir = Paths.get(basePath, subDirectory);
            Files.createDirectories(dir);
            Path target = dir.resolve(safeFilename);
            storedFiles.ecrire(file, target);
            return subDirectory + "/" + safeFilename;
        } catch (IOException e) {
            throw new BusinessException("Erreur lors du stockage du fichier: " + e.getMessage());
        }
    }

    @Override
    public Resource load(String filePath) {
        try {
            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            Path resolved = base.resolve(filePath).normalize();
            if (!resolved.startsWith(base)) {
                throw new BusinessException("Chemin de fichier invalide: " + filePath);
            }
            // Fichier en clair : on rend la main au flux, comme avant. Chiffré :
            // le contenu déchiffré part en mémoire, et ByteArrayResource porte
            // la bonne longueur — le contrôleur la reprend telle quelle.
            if (storedFiles.estChiffre(resolved)) {
                return new ByteArrayResource(storedFiles.lireDechiffre(resolved));
            }
            return new UrlResource(resolved.toUri());
        } catch (IOException e) {
            throw new BusinessException("Lecture du fichier impossible: " + filePath);
        }
    }

    @Override
    public void delete(String filePath) {
        try {
            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            Path resolved = base.resolve(filePath).normalize();
            if (!resolved.startsWith(base)) {
                throw new BusinessException("Chemin de fichier invalide: " + filePath);
            }
            // Par le dépôt : un fichier d'avant la bascule vit encore sur le
            // disque, et ne supprimer que dans le seau le laisserait revenir
            // à la lecture suivante — effacé à l'écran, présent en fait.
            storedFiles.supprimer(resolved);
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier physique: {}", filePath);
        }
    }
}
