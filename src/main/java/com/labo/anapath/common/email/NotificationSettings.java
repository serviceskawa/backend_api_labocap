package com.labo.anapath.common.email;

import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Accès centralisé aux réglages ({@code setting_apps}) utilisés par les notifications
 * mail métier : nom du laboratoire, liste des emails administrateurs, services activés.
 *
 * <p>Chaque clé est résolue d'abord au niveau de la branche, puis en repli global —
 * même convention que {@code ScheduledTasksService}. Les listes d'emails acceptent
 * les séparateurs {@code | ; ,} (Laravel utilise {@code |}).
 */
@Component
@RequiredArgsConstructor
public class NotificationSettings {

    private final SettingAppRepository settingAppRepository;

    /** Nom du laboratoire pour l'en-tête / la signature des emails. */
    public String labName(UUID branchId) {
        return resolve("lab_name", branchId).orElse("le laboratoire");
    }

    /** Liste des emails administrateurs (clé {@code admin_mails}). */
    public List<String> adminEmails(UUID branchId) {
        return parseEmails(resolve("admin_mails", branchId).orElse(""));
    }

    /**
     * Indique si un service est activé dans la clé {@code services}.
     * Si la clé n'est pas configurée, on considère le service actif (ne pas bloquer).
     */
    public boolean serviceEnabled(UUID branchId, String service) {
        return resolve("services", branchId)
                .map(value -> Arrays.stream(value.split("[|;,]"))
                        .map(String::trim)
                        .anyMatch(service::equalsIgnoreCase))
                .orElse(true);
    }

    private java.util.Optional<String> resolve(String key, UUID branchId) {
        return settingAppRepository.findByKeyAndBranchId(key, branchId)
                .or(() -> settingAppRepository.findByKey(key))
                .map(SettingApp::getValue);
    }

    /** Découpe une liste d'emails sur {@code | ; ,}, en ne gardant que les adresses valides. */
    public static List<String> parseEmails(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split("[|;,]")) {
            String email = part.trim();
            if (!email.isEmpty() && email.contains("@")) {
                result.add(email);
            }
        }
        return result;
    }
}
