/**
 * Noyau partagé : types utilisés par tous les modules (utilisateur courant,
 * exceptions de domaine, objets valeur communs, configuration transverse).
 * Module ouvert : ses sous-paquets sont accessibles aux autres modules.
 */
@ApplicationModule(displayName = "Noyau partagé", type = ApplicationModule.Type.OPEN)
package com.smartrecruit.shared;

import org.springframework.modulith.ApplicationModule;
