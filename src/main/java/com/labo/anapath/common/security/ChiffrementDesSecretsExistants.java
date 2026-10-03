package com.labo.anapath.common.security;

import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Au démarrage, chiffre les secrets TOTP encore en clair (lot 11).
 *
 * <p>Idempotent : ne touche que les lignes sans la marque de format, donc rien
 * la fois suivante. Sans clé configurée, ne fait rien.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChiffrementDesSecretsExistants implements ApplicationRunner {

    private final ChiffreurDeSecrets chiffreur;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!chiffreur.estActif()) return;
        List<Object[]> enClair = userRepository.secretsTotpEnClair();
        for (Object[] ligne : enClair) {
            userRepository.remplacerSecretTotp((java.util.UUID) ligne[0], chiffreur.chiffrer((String) ligne[1]));
        }
        if (!enClair.isEmpty()) {
            log.info("Secrets TOTP chiffrés au démarrage : {}", enClair.size());
        }
    }
}
