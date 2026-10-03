package com.labo.anapath.common.security;

import com.labo.anapath.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Règle de mot de passe (lot 11), appliquée à la création, au changement et à
 * la réinitialisation : 12 caractères au moins, pas un mot de passe courant ni
 * bâti sur l'un d'eux, pas l'adresse ni le nom de la personne.
 *
 * <p>Pas d'exigence de majuscule ou de symbole : c'est la longueur et l'absence
 * de mot connu qui coûtent à l'attaquant, pas le « ! » final que tout le monde
 * ajoute.</p>
 */
@Component
public class PolitiqueDeMotDePasse {

    static final int LONGUEUR_MINIMALE = 12;
    /** En deçà, un mot courant contenu dans le mot de passe ne dit rien (« 123 », « amour »). */
    private static final int LONGUEUR_MOT_COURANT_SIGNIFICATIF = 6;

    private final Set<String> courants;

    public PolitiqueDeMotDePasse() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/mots-de-passe-courants.txt"), StandardCharsets.UTF_8))) {
            courants = r.lines().map(String::trim).filter(l -> !l.isEmpty())
                    .map(l -> l.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        } catch (Exception e) {
            throw new IllegalStateException("Liste des mots de passe courants introuvable", e);
        }
    }

    /**
     * @throws BusinessException (422) avec la raison du refus, lisible par la personne
     */
    public void verifier(String motDePasse, String email, String prenom, String nom) {
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MINIMALE) {
            throw new BusinessException("Le mot de passe doit contenir au moins " + LONGUEUR_MINIMALE + " caractères.");
        }
        String bas = motDePasse.toLowerCase(Locale.ROOT);
        if (courants.contains(bas)) {
            throw new BusinessException("Ce mot de passe est trop courant.");
        }
        for (String courant : courants) {
            if (courant.length() >= LONGUEUR_MOT_COURANT_SIGNIFICATIF && bas.contains(courant)) {
                throw new BusinessException("Le mot de passe ne doit pas contenir un mot de passe courant (« " + courant + " »).");
            }
        }
        for (String perso : new String[] {prenom, nom, partieLocale(email)}) {
            if (perso != null && perso.trim().length() >= 3 && bas.contains(perso.trim().toLowerCase(Locale.ROOT))) {
                throw new BusinessException("Le mot de passe ne doit pas contenir votre nom ni votre adresse.");
            }
        }
    }

    private static String partieLocale(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
