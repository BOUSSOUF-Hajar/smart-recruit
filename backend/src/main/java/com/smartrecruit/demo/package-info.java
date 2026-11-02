/**
 * Données de démonstration (US-56), chargées seulement avec le profil {@code demo} sur une base vide.
 * <p>
 * Module à part : il n'est appelé par aucun autre et n'appelle aucun service métier. Il écrit
 * directement en SQL, comme le ferait un script de chargement, en respectant les règles que la base
 * ne vérifie pas seule (historique cohérent, entretien terminé avant une proposition, etc.).
 */
@ApplicationModule(displayName = "Données de démonstration")
package com.smartrecruit.demo;

import org.springframework.modulith.ApplicationModule;
