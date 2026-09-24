package com.labo.anapath.common;

/**
 * Discipline à laquelle appartient une analyse, une catégorie, un bon d'examen ou
 * un compte-rendu.
 *
 * <p>Toutes les données antérieures au module Biologie sont en {@link #PATHOLOGY}.
 * Les listes et compteurs existants filtrent sur {@link #PATHOLOGY} par défaut, afin
 * que les écrans d'anatomie pathologique restent inchangés quand la biologie est activée.
 */
public enum Discipline {
    /** Anatomie et cytologie pathologiques : macroscopie, compte-rendu rédigé. */
    PATHOLOGY,
    /** Biologie clinique : paramètres chiffrés, valeurs de référence, cultures. */
    BIOLOGY
}
