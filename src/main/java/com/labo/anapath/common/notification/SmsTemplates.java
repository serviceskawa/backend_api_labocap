package com.labo.anapath.common.notification;

import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Textes des SMS envoyés aux patients, modifiables depuis l'écran Paramètres.
 *
 * <p>Ces messages portent le nom du laboratoire, ses horaires et son adresse :
 * autant de choses qui changent sans qu'un déploiement soit justifié. Ils vivent
 * donc dans {@code setting_apps}, sous l'onglet « Communication Mobile ».</p>
 *
 * <p><b>Le défaut reste dans le code, et c'est délibéré.</b> Une clé absente,
 * vide ou effacée par mégarde ne doit pas faire partir un SMS vide : le message
 * livré prend alors le relais. C'est aussi ce qui évite d'avoir à semer ces
 * valeurs par migration dans chaque branche.</p>
 */
@Service
@RequiredArgsConstructor
public class SmsTemplates {

    /** Clé {@code setting_apps} du SMS annonçant un résultat disponible au retrait. */
    public static final String CLE_SMS_RESULTAT = "sms_resultat_body";

    /** Clé {@code setting_apps} du SMS annonçant une facture téléchargeable. */
    public static final String CLE_SMS_FACTURE = "sms_facture_body";

    /** Texte livré du SMS de résultat, repris tel quel du Laravel d'origine. */
    public static final String DEFAUT_SMS_RESULTAT =
            "Bonjour c'est le cabinet medical Anathomie pathologique adechinan situé à fifadji "
            + "vos résultats d'analyse sont maintenant disponible vous pouvez venir les recupérer "
            + "à tout moment pendant nos heures d'ouvertures. "
            + "Nous sommes ouvert du Lundi au vendredi de 08h à 17h Merci de votre confiance";

    /**
     * Texte livré du SMS de facture. Volontairement court : au-delà de
     * 160 caractères, l'opérateur facture plusieurs segments et certains
     * téléphones tronquent l'affichage, ce qui couperait le lien.
     */
    public static final String DEFAUT_SMS_FACTURE =
            "Bonjour, votre facture {code} du Centre Adechina d'Anatomie Pathologique "
            + "est disponible : {lien}";

    private final SettingAppRepository settingAppRepository;

    /**
     * Le texte du SMS de résultat, tel que paramétré ou, à défaut, tel que livré.
     *
     * @param branchId branche du compte-rendu ; {@code null} accepté
     */
    public String smsResultat(UUID branchId) {
        return modele(CLE_SMS_RESULTAT, branchId, DEFAUT_SMS_RESULTAT);
    }

    /**
     * Le texte du SMS de facture, jetons {@code {code}} et {@code {lien}} remplacés.
     *
     * @param branchId branche de la facture ; {@code null} accepté
     * @param code     code de la facture ; chaîne vide acceptée
     * @param lien     URL publique de téléchargement
     */
    public String smsFacture(UUID branchId, String code, String lien) {
        return remplacer(modele(CLE_SMS_FACTURE, branchId, DEFAUT_SMS_FACTURE),
                Map.of("{code}", code == null ? "" : code, "{lien}", lien));
    }

    /**
     * Valeur paramétrée d'une clé, ou le défaut si elle est absente ou vide.
     *
     * <p><b>Résolution en trois temps :</b> le texte de la branche concernée, puis
     * celui de la branche la plus ancienne — la branche mère, dont le libellé fait
     * référence tant qu'une antenne n'a pas écrit le sien — puis le texte livré.
     * Le repli sur la branche mère évite qu'ouvrir une antenne ne fasse repartir
     * ses SMS sur le texte d'usine.</p>
     *
     * <p>Le cas « vide » compte autant que le cas « absente » : l'écran Paramètres
     * enregistre une chaîne vide dès qu'un champ est effacé, et un SMS sans texte
     * serait envoyé — et facturé — pour rien.</p>
     *
     * <p>La lecture ne passe pas par {@code findByKey} : cette méthode lève dès que
     * deux branches définissent la même clé, ce qui est justement le cas normal
     * pour un réglage éditable par branche.</p>
     */
    private String modele(String cle, UUID branchId, String defaut) {
        Optional<String> propreALaBranche = (branchId == null)
                ? Optional.empty()
                : settingAppRepository.findByKeyAndBranchId(cle, branchId)
                        .map(SettingApp::getValue)
                        .filter(valeur -> !valeur.isBlank());

        return propreALaBranche
                .or(() -> settingAppRepository.findByKeyInOrderByCreatedAtAsc(List.of(cle)).stream()
                        .map(SettingApp::getValue)
                        .filter(valeur -> valeur != null && !valeur.isBlank())
                        .findFirst())
                .orElse(defaut);
    }

    /**
     * Remplace les jetons du modèle.
     *
     * <p>Le remplacement est littéral, et non un {@code String.format} : le texte
     * vient de l'écran Paramètres, où un {@code %} saisi par un utilisateur ferait
     * échouer le formatage et perdre le SMS.</p>
     */
    private String remplacer(String modele, Map<String, String> jetons) {
        String resultat = modele;
        for (Map.Entry<String, String> jeton : jetons.entrySet()) {
            resultat = resultat.replace(jeton.getKey(), jeton.getValue());
        }
        return resultat;
    }
}
