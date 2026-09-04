package com.labo.anapath.appel;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un appel en cours, tel que le serveur le connaît.
 *
 * <h2>En mémoire, et non en base</h2>
 *
 * <p>Un appel ne survit pas au redémarrage du serveur — et c'est juste : les
 * liaisons WebSocket non plus. Le retrouver en base après un redémarrage ne
 * rendrait qu'une coquille dont tous les participants sont déjà partis. Ce qui
 * doit rester est écrit à la fin, dans le journal.</p>
 *
 * <h2>La limite de quatre</h2>
 *
 * <p>Chaque téléphone parle directement à tous les autres : à quatre, cela fait
 * trois liaisons montantes par appareil, ce qu'un réseau mobile tient. À six,
 * cinq liaisons — la voix se hache, et c'est le réseau du plus faible qui décide
 * pour tout le monde. Passer cette limite demande un serveur de média, pas un
 * chiffre plus grand ici.</p>
 */
@Getter
public class Appel {

    /** Au-delà, la maille coûte plus qu'elle ne rend. */
    public static final int MAXIMUM = 4;

    private final UUID id = UUID.randomUUID();
    private final UUID dossier;
    private final UUID branche;
    private final UUID initiateur;
    private final LocalDateTime debut = LocalDateTime.now();

    /** Qui a été sonné — pour savoir qui peut encore entrer. */
    private final Set<UUID> conviés = ConcurrentHashMap.newKeySet();

    /** Qui décroche réellement, et depuis quand. */
    private final Map<UUID, LocalDateTime> présents = new ConcurrentHashMap<>();

    /** Qui a refusé — pour ne pas le resonner en boucle. */
    private final Set<UUID> refus = ConcurrentHashMap.newKeySet();

    public Appel(UUID dossier, UUID branche, UUID initiateur) {
        this.dossier = dossier;
        this.branche = branche;
        this.initiateur = initiateur;
    }

    public boolean peutEntrer(UUID qui) {
        return (conviés.contains(qui) || initiateur.equals(qui))
                && présents.size() < MAXIMUM;
    }

    public boolean estPresent(UUID qui) {
        return présents.containsKey(qui);
    }

    /**
     * L'appel est fini quand il ne reste personne — ou une seule personne.
     *
     * <p>Rester seul en ligne n'est pas un appel : c'est un téléphone allumé
     * dans une poche, qui continue de consommer la batterie et le forfait.</p>
     */
    public boolean estTermine() {
        return présents.size() <= 1;
    }
}
