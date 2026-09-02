package com.labo.anapath.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URI;

/**
 * Un seau S3.
 *
 * <h2>Ce qu'AWS reçoit</h2>
 *
 * <p>Des octets déjà chiffrés par le serveur (AES-256-GCM). Le seau ne contient
 * aucune lame lisible, aucun bon d'examen déchiffrable : un accès au compte AWS,
 * volé ou légal, ne rend rien. C'est ce qui permet de confier le stockage à un
 * tiers sans lui confier le contenu médical.</p>
 *
 * <p>Corollaire à connaître : on ne peut pas servir ces objets par un lien signé
 * directement au navigateur, qui ne saurait pas les déchiffrer. Les images
 * continuent donc de passer par l'API, comme aujourd'hui.</p>
 *
 * <h2>N'existe que si on le configure</h2>
 *
 * <p>Sans {@code app.storage.s3.enabled}, ce composant n'est pas créé du tout et
 * {@link StoredFiles} ne connaît que le disque. Une installation qui n'a pas de
 * compte AWS continue de fonctionner sans rien changer.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.s3.enabled", havingValue = "true")
public class DepotS3 implements DepotDOctets {

    private final S3Client client;
    private final String seau;
    private final String prefixe;

    public DepotS3(
            @Value("${app.storage.s3.bucket:}") String seau,
            @Value("${app.storage.s3.region:eu-west-3}") String region,
            @Value("${app.storage.s3.prefix:}") String prefixe,
            @Value("${app.storage.s3.access-key:}") String cle,
            @Value("${app.storage.s3.secret-key:}") String secret,
            @Value("${app.storage.s3.endpoint:}") String pointDEntree) {

        if (seau == null || seau.isBlank()) {
            throw new IllegalStateException(
                    "app.storage.s3.enabled est vrai mais app.storage.s3.bucket est vide. "
                    + "Refuser de démarrer vaut mieux qu'accepter des clichés que "
                    + "personne ne saura relire.");
        }
        this.seau = seau.trim();
        // Un préfixe sans « / » final collerait au premier segment de la clé.
        String p = prefixe == null ? "" : prefixe.trim();
        this.prefixe = p.isEmpty() || p.endsWith("/") ? p : p + "/";

        var constructeur = S3Client.builder().region(Region.of(region));

        // Sans clés explicites, la chaîne d'AWS prend le relais : rôle IAM de la
        // machine, variables d'environnement, fichier ~/.aws. C'est ce qu'il faut
        // sur une instance EC2, où poser une clé en dur serait un recul.
        if (cle != null && !cle.isBlank() && secret != null && !secret.isBlank()) {
            constructeur.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(cle.trim(), secret.trim())));
        } else {
            constructeur.credentialsProvider(DefaultCredentialsProvider.create());
        }

        // Un point d'entrée explicite permet de viser MinIO ou un autre service
        // compatible — utile pour éprouver la bascule sans compte AWS.
        if (pointDEntree != null && !pointDEntree.isBlank()) {
            constructeur.endpointOverride(URI.create(pointDEntree.trim()))
                    .forcePathStyle(true);
        }

        this.client = constructeur.build();
        log.info("Stockage S3 actif : seau={} région={}{}", this.seau, region,
                this.prefixe.isEmpty() ? "" : " préfixe=" + this.prefixe);
    }

    private String objet(String cle) {
        return prefixe + cle;
    }

    @Override
    public String nom() {
        return "S3";
    }

    @Override
    public void ecrire(String cle, byte[] octets) throws IOException {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(seau).key(objet(cle)).build(),
                    RequestBody.fromBytes(octets));
        } catch (S3Exception e) {
            throw new IOException("Écriture S3 impossible : " + cle, e);
        }
    }

    @Override
    public byte[] lire(String cle) throws IOException {
        try {
            ResponseBytes<?> reponse = client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(seau).key(objet(cle)).build());
            return reponse.asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            // Même raisonnement que pour la taille : un refus laisse sa chance
            // au disque plutôt que de casser l'écran.
            if (e.statusCode() == 403 || e.statusCode() == 404) {
                if (e.statusCode() == 403) {
                    log.warn("S3 refuse la lecture de « {} » (403) — repli sur "
                            + "le disque. Vérifier la politique IAM du seau {}.",
                            cle, seau);
                }
                return null;
            }
            throw new IOException("Lecture S3 impossible : " + cle, e);
        }
    }

    @Override
    public byte[] lireLeDebut(String cle, int combien) throws IOException {
        try {
            // Une requête d'intervalle : reconnaître la marque d'un fichier
            // chiffré ne doit pas coûter le rapatriement d'un cliché entier.
            ResponseBytes<?> reponse = client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(seau).key(objet(cle))
                            .range("bytes=0-" + (combien - 1)).build());
            return reponse.asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            // Un objet plus court que l'intervalle demandé : ce n'est pas une
            // panne, c'est un petit fichier.
            if (e.statusCode() == 416) return lire(cle);
            if (e.statusCode() == 403 || e.statusCode() == 404) return null;
            throw new IOException("Lecture S3 impossible : " + cle, e);
        }
    }

    @Override
    public boolean existe(String cle) throws IOException {
        return taille(cle) >= 0;
    }

    /**
     * La taille, ou -1 si le seau ne peut rien en dire.
     *
     * <h2>Pourquoi un refus vaut « absent » et non « panne »</h2>
     *
     * <p>Un seau mal autorisé répond 403 là où un objet manquant donnerait 404 :
     * sans droit d'inventaire, AWS ne distingue pas « tu n'as pas accès » de
     * « cela n'existe pas », et choisit le premier pour ne rien révéler.</p>
     *
     * <p>Faire échouer la lecture sur ce 403 rendait une erreur interne à
     * l'écran — <em>y compris pour les clichés d'avant la bascule</em>, qui
     * dorment paisiblement sur le disque à un repli de distance. Une
     * configuration IAM incomplète cassait ainsi des années de dossiers.</p>
     *
     * <p>On rend donc -1, la lecture se reporte sur le disque, et
     * l'avertissement dit ce qu'il faut corriger. L'administrateur voit le
     * problème ; l'agent voit ses images.</p>
     */
    @Override
    public long taille(String cle) throws IOException {
        try {
            HeadObjectResponse tete = client.headObject(
                    HeadObjectRequest.builder().bucket(seau).key(objet(cle)).build());
            return tete.contentLength();
        } catch (NoSuchKeyException e) {
            return -1;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return -1;
            if (e.statusCode() == 403) {
                log.warn("S3 refuse l'accès à « {} » (403). Vérifier que la "
                        + "politique IAM couvre « arn:aws:s3:::{}/* » ET "
                        + "« arn:aws:s3:::{} », et qu'elle accorde s3:ListBucket "
                        + "— sans ce droit, un objet absent est signalé 403 et "
                        + "non 404. Repli sur le disque en attendant.",
                        cle, seau, seau);
                return -1;
            }
            throw new IOException("Interrogation S3 impossible : " + cle, e);
        }
    }

    @Override
    public void supprimer(String cle) throws IOException {
        try {
            client.deleteObject(
                    DeleteObjectRequest.builder().bucket(seau).key(objet(cle)).build());
        } catch (S3Exception e) {
            // Refusée : on le dit, mais on laisse StoredFiles supprimer sur le
            // disque. L'inverse laisserait un fichier que l'agent croit effacé.
            if (e.statusCode() == 403) {
                log.warn("S3 refuse la suppression de « {} » (403).", cle);
                return;
            }
            throw new IOException("Suppression S3 impossible : " + cle, e);
        }
    }
}
