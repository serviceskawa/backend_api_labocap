package com.labo.anapath.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Le disque du serveur — le stockage d'origine.
 *
 * <p>Toujours présent, même quand S3 est actif : c'est lui qui détient les
 * fichiers d'avant la bascule, et une lecture qui ne trouve rien dans le seau
 * revient le lui demander.</p>
 */
@Slf4j
@Component
public class DepotDisque implements DepotDOctets {

    private final Path racine;

    public DepotDisque(@Value("${app.storage.path:/tmp/labo/storage}") String chemin) {
        this.racine = Paths.get(chemin).toAbsolutePath().normalize();
    }

    public Path racine() {
        return racine;
    }

    /**
     * Le chemin absolu d'une clé, en refusant d'en sortir.
     *
     * <p>Une clé contenant « .. » remonterait hors du dossier de stockage et
     * donnerait accès au reste du serveur. Le contrôle est ici et non chez
     * l'appelant : il y a trois appelants, et il en suffirait d'un qui oublie.</p>
     */
    private Path resoudre(String cle) throws IOException {
        Path p = racine.resolve(cle).normalize();
        if (!p.startsWith(racine)) {
            throw new IOException("Chemin hors du dossier de stockage : " + cle);
        }
        return p;
    }

    @Override
    public String nom() {
        return "disque";
    }

    @Override
    public void ecrire(String cle, byte[] octets) throws IOException {
        Path cible = resoudre(cle);
        Files.createDirectories(cible.getParent());
        Files.write(cible, octets);
    }

    @Override
    public byte[] lire(String cle) throws IOException {
        Path p = resoudre(cle);
        return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
    }

    @Override
    public byte[] lireLeDebut(String cle, int combien) throws IOException {
        Path p = resoudre(cle);
        if (!Files.isRegularFile(p)) return null;
        byte[] tampon = new byte[combien];
        try (InputStream in = Files.newInputStream(p)) {
            int lus = in.readNBytes(tampon, 0, combien);
            if (lus == combien) return tampon;
            byte[] court = new byte[lus];
            System.arraycopy(tampon, 0, court, 0, lus);
            return court;
        }
    }

    @Override
    public boolean existe(String cle) throws IOException {
        return Files.isRegularFile(resoudre(cle));
    }

    @Override
    public long taille(String cle) throws IOException {
        Path p = resoudre(cle);
        return Files.isRegularFile(p) ? Files.size(p) : -1;
    }

    @Override
    public void supprimer(String cle) throws IOException {
        Files.deleteIfExists(resoudre(cle));
    }
}
