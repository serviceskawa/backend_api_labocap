package com.labo.anapath.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileStorageService fileStorageService;
    private final StoredFiles storedFiles;

    @GetMapping("/**")
    public ResponseEntity<Resource> getFile(@PathVariable(required = false) String relativePath,
                                            jakarta.servlet.http.HttpServletRequest request) throws IOException {
        String path = request.getRequestURI().replaceFirst("/api/v1/files/", "");
        Path filePath = fileStorageService.resolve(path);
        Path basePath = fileStorageService.resolve("");

        if (!filePath.startsWith(basePath)) {
            log.warn("Tentative de path traversal détectée: {}", path);
            return ResponseEntity.status(403).build();
        }

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            return ResponseEntity.notFound().build();
        }

        String filename = filePath.getFileName().toString();

        // Servir avec un Content-Length connu (FileSystemResource) plutôt qu'un
        // StreamingResponseBody en Transfer-Encoding: chunked. Le chunked était
        // rejeté par le navigateur (net::ERR_INCOMPLETE_CHUNKED_ENCODING) → l'aperçu
        // et le téléchargement des pièces jointes échouaient.
        //
        // La sorte de fichier se lit dans le fichier lui-même, pas dans la
        // configuration : les images d'avant le chiffrement restent servies par
        // le flux, sans rien charger en mémoire, et le rattrapage de l'existant
        // peut donc attendre.
        Resource resource;
        long longueur;
        boolean chiffre = storedFiles.estChiffre(filePath);
        if (chiffre) {
            // Le Content-Length doit être celui du CLAIR. La taille sur disque
            // compte en plus l'en-tête et le sceau : la poser tronquerait la
            // réponse de quelques dizaines d'octets, et l'image serait cassée.
            byte[] clair = storedFiles.lireDechiffre(filePath);
            resource = new ByteArrayResource(clair);
            longueur = clair.length;
        } else {
            resource = new FileSystemResource(filePath);
            longueur = Files.size(filePath);
        }

        String contentType = detectContentType(filePath, chiffre);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(longueur)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                // Le CSP global de l'API est « default-src 'none' », ce qui implique
                // « object-src 'none' » et bloque le lecteur PDF de Chrome (« Échec de
                // chargement du document PDF »). On le pose ici (les writers Spring
                // Security ne remplacent pas un en-tête déjà présent) avec une policy
                // permettant l'affichage inline des fichiers servis (PDF, images).
                .header("Content-Security-Policy",
                        "default-src 'self'; object-src 'self' blob: data:; "
                                + "img-src 'self' blob: data:; media-src 'self' blob: data:; "
                                + "style-src 'self' 'unsafe-inline'; frame-ancestors 'self'")
                .header("X-Frame-Options", "SAMEORIGIN")
                .body(resource);
    }

    /**
     * @param chiffre si vrai, le type se déduit du seul nom. {@code probeContentType}
     *                consulte le contenu sur certaines plateformes ; sur des octets
     *                chiffrés il rendrait « application/octet-stream », et le
     *                navigateur proposerait de télécharger une image au lieu de
     *                l'afficher. L'extension, elle, est conservée par le chiffrement.
     */
    private String detectContentType(Path path, boolean chiffre) {
        if (!chiffre) {
            try {
                String detected = Files.probeContentType(path);
                if (detected != null) return detected;
            } catch (IOException ignored) {}
        }
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf"))  return "application/pdf";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".gif"))  return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
