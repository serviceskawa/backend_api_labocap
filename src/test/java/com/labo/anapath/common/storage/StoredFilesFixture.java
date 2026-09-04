package com.labo.anapath.common.storage;

import org.springframework.beans.factory.ObjectProvider;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Fabrique un {@link StoredFiles} dans l'un ou l'autre régime, sans contexte Spring.
 *
 * <p>{@link ObjectProvider} n'a pas d'implémentation triviale dans Spring, et
 * deux classes d'essai en ont besoin — celle du point de passage et celle du
 * service de stockage, qui ne teste pas le chiffrement mais doit bien construire
 * son collaborateur.</p>
 */
final class StoredFilesFixture {

    private StoredFilesFixture() {}

    /**
     * La racine par défaut des essais.
     *
     * <p>Les tests écrivent dans des dossiers temporaires absolus ; le dépôt
     * disque a besoin d'une racine pour en dériver ses clés. La racine du
     * système convient : elle contient tous les chemins qu'un test peut
     * produire, et le dépôt ne s'en sert que pour relativiser.</p>
     */
    private static final String RACINE = java.io.File.separator;

    /** Le régime d'avant : aucun chiffreur, comme avec le drapeau à faux. */
    static StoredFiles enClair() {
        return enClair(RACINE);
    }

    static StoredFiles enClair(String racine) {
        return StoredFiles.surLeDisqueSeul(fournisseur(null), new DepotDisque(racine));
    }

    static StoredFiles chiffrant(FileCipher c) {
        return chiffrant(c, RACINE);
    }

    static StoredFiles chiffrant(FileCipher c, String racine) {
        return StoredFiles.surLeDisqueSeul(fournisseur(c), new DepotDisque(racine));
    }

    static FileCipher chiffreurAleatoire() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return new FileCipher(Base64.getEncoder().encodeToString(k));
    }

    static ObjectProvider<FileCipher> fournisseur(FileCipher c) {
        return new ObjectProvider<>() {
            @Override public FileCipher getIfAvailable() { return c; }
            @Override public FileCipher getObject() { return c; }
            @Override public FileCipher getObject(Object... args) { return c; }
            @Override public FileCipher getIfUnique() { return c; }
        };
    }
}
