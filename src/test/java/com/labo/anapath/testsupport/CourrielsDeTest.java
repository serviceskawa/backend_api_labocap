package com.labo.anapath.testsupport;

import com.labo.anapath.common.email.EmailServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

/**
 * Le service de courriel des tests : retient le dernier code de connexion au
 * lieu de l'envoyer.
 *
 * <p>Le code n'existe nulle part ailleurs en clair — la base n'en garde que
 * l'empreinte. C'est donc ici, à la sortie, que le test le lit pour franchir
 * le challenge comme le ferait une personne devant sa boîte aux lettres.</p>
 *
 * <p>Les autres courriels suivent le chemin normal : le serveur SMTP de test
 * n'existe pas, l'envoi échoue et est journalisé, comme avant.</p>
 */
@Component
@Primary
public class CourrielsDeTest extends EmailServiceImpl {

    // Statique : @Async fait du bean un proxy d'interface, qu'on ne peut injecter
    // sous ce type. Le test lit ici, sans injection.
    private static volatile String dernierCode;
    // L'envoi est asynchrone : le test peut lire avant que le code soit posé,
    // et récupérer celui de la connexion précédente. On compte les envois et
    // chaque lecture attend un code plus récent que le dernier lu.
    private static volatile long envois;
    private static long lus;

    public CourrielsDeTest(JavaMailSender mailSender, TemplateEngine templateEngine, Environment environment) {
        super(mailSender, templateEngine, environment);
    }

    @Override
    public void sendOtp(String to, String firstname, String otp) {
        dernierCode = otp;
        envois++;
    }

    /** Le code de la dernière connexion, attendu jusqu'à 5 s s'il n'est pas encore parti. */
    public static synchronized String dernierCode() {
        long limite = System.currentTimeMillis() + 5_000;
        while (envois <= lus && System.currentTimeMillis() < limite) {
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        lus = envois;
        return dernierCode;
    }
}
