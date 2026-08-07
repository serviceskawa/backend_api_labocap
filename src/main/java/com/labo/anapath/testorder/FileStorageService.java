package com.labo.anapath.testorder;

import com.labo.anapath.common.storage.StoredFiles;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StoredFiles storedFiles;

    @Value("${app.storage.path:/tmp/labo/storage}")
    private String storagePath;

    private Path getUploadDir() {
        return getUploadDir("examen_images");
    }

    private Path getUploadDir(String subDir) {
        Path dir = Paths.get(storagePath, subDir).toAbsolutePath().normalize();
        try { Files.createDirectories(dir); } catch (IOException e) { throw new RuntimeException(e); }
        return dir;
    }

    public String store(MultipartFile file) throws IOException {
        return store(file, "examen_images");
    }

    /**
     * Range un fichier dans un sous-dossier du stockage (ex. « preuves » pour les
     * justificatifs de dépense) et renvoie son chemin relatif, préfixe compris.
     *
     * @param subDir sous-dossier cible, créé au besoin
     */
    public String store(MultipartFile file, String subDir) throws IOException {
        Path uploadDir = getUploadDir(subDir);
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + (ext != null ? "." + ext.toLowerCase() : "");
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Chemin de fichier invalide");
        }
        storedFiles.ecrire(file, target);
        return subDir + "/" + filename;
    }

    public void delete(String filename) throws IOException {
        // `filename` (issu de store()) inclut déjà le préfixe « examen_images/ »,
        // on le résout donc depuis la racine du stockage, pas depuis examen_images/.
        Path base = Paths.get(storagePath).toAbsolutePath().normalize();
        Path target = base.resolve(filename).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("Chemin de fichier invalide");
        }
        Files.deleteIfExists(target);
    }

    public String getUrl(String filename) {
        // `filename` inclut déjà « examen_images/ » (renvoyé par store()) : ne pas
        // le re-préfixer, sinon l'URL contient examen_images/examen_images/… → 404.
        return "/api/v1/files/" + filename;
    }
}
