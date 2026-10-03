package com.labo.anapath.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Chiffre en base un secret qu'il faut relire en clair — aujourd'hui le secret
 * TOTP de l'application d'authentification ({@code users.two_factor_secret}).
 *
 * <p>Sans clé configurée, la valeur passe telle quelle : les installations qui
 * n'ont pas posé de clé continuent de fonctionner comme avant. Une valeur
 * ancienne, en clair, se lit encore ; elle est rechiffrée au démarrage par
 * {@link ChiffrementDesSecretsExistants}.</p>
 */
@Component
@Converter
@RequiredArgsConstructor
public class SecretChiffreConverter implements AttributeConverter<String, String> {

    private final ChiffreurDeSecrets chiffreur;

    @Override
    public String convertToDatabaseColumn(String clair) {
        if (clair == null || !chiffreur.estActif()) return clair;
        String chiffre = chiffreur.chiffrer(clair);
        return chiffre != null ? chiffre : clair;
    }

    @Override
    public String convertToEntityAttribute(String enBase) {
        if (enBase == null || !ChiffreurDeSecrets.estChiffre(enBase)) return enBase;
        return chiffreur.dechiffrer(enBase);
    }
}
