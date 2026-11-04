# SmartRecruit — Spécification de l'API REST

Oct 19, 2026 · @Hajar

## 0. Conventions

Toutes les règles ci-dessous s'appliquent à chaque endpoint ; les sections suivantes ne précisent que ce qui en diffère.

| Sujet | Règle |
| --- | --- |
| Base | `/api/v1` ; tous les chemins de ce document sont relatifs à cette base |
| Format | JSON en UTF-8 ; propriétés en `camelCase` ; `multipart/form-data` uniquement pour le CV |
| Authentification | En-tête `Authorization: Bearer <accessToken>` ; le refresh token voyage seulement dans le cookie `refresh_token` (HttpOnly, Secure, SameSite=Strict, chemin `/api/v1/auth`) |
| Identifiants | UUID en chaîne de caractères |
| Dates | ISO 8601 en UTC pour les instants (`2026-10-19T14:30:00Z`) ; `YYYY-MM-DD` pour les dates seules |
| Énumérations | Valeurs en majuscules, identiques au modèle (`SHORTLISTED`) ; le frontend gère les libellés |
| Pagination | Paramètres `page` (à partir de 0), `size` (défaut 20, maximum 100), `sort` (`champ,asc` ou `champ,desc`, liste blanche par endpoint) |
| Réponse paginée | `{ "content": [...], "page": 0, "size": 20, "totalElements": 45, "totalPages": 3 }` |
| Création | `201 Created` + en-tête `Location` + ressource créée dans le corps |
| Modification | `200 OK` + ressource à jour ; `204 No Content` pour une suppression |
| Traitement asynchrone | `202 Accepted` + `{ "taskId": "..." }` + en-tête `Location` vers la tâche |
| Actions de cycle de vie | Sous-ressources en `POST` : `/jobs/{id}/publish`, `/interviews/{id}/cancel` ; seul le statut d'une candidature passe par `PATCH`, car sa cible est une donnée |
| Concurrence | Les ressources modifiables exposent `version` ; le client la renvoie, et une version obsolète donne `409 CONCURRENT_MODIFICATION` |
| Périmètre | Les collections sont filtrées automatiquement par le périmètre de l'appelant : jamais d'identifiant d'entreprise ou de candidat dans les paramètres |
| Hors périmètre | `404 RESOURCE_NOT_FOUND`, jamais `403`, pour une ressource qui existe mais n'appartient pas à l'appelant |
| Corrélation | En-tête `X-Correlation-Id` accepté en entrée, toujours renvoyé |

### Format des erreurs

Toutes les erreurs suivent Problem Details (RFC 9457), type de contenu `application/problem+json` :

```json
{
  "type": "https://smartrecruit.dev/errors/invalid-status-transition",
  "title": "Transition de statut interdite",
  "status": 409,
  "detail": "Une candidature HIRED ne peut plus changer de statut.",
  "instance": "/api/v1/applications/0192a7c4-.../status",
  "code": "INVALID_STATUS_TRANSITION",
  "correlationId": "7f3c9a1e2b",
  "errors": []
}
```

`errors` n'est rempli que pour `VALIDATION_FAILED` : `[{ "field": "email", "message": "doit être une adresse valide" }]`. Les codes viennent du catalogue de l'analyse fonctionnelle (section 7) ; les erreurs communes à tous les endpoints protégés (`401`, `403 ACCESS_DENIED`, `400 VALIDATION_FAILED`, `429 RATE_LIMITED`, `500 INTERNAL_ERROR`) ne sont pas répétées dans les tableaux.

### Lecture des tableaux

**Rôle** : `Public`, `Connecté` (tout rôle), `C` (CANDIDATE), `R` (RECRUITER), `A` (ADMIN). **Erreurs** : seulement celles propres à l'endpoint. Les exemples JSON montrent les champs utiles, sans `createdAt`/`updatedAt` quand ils n'apportent rien.

## 1. Vue d'ensemble

L'API compte 86 endpoints métier en 9 groupes, plus les endpoints techniques ; chaque préfixe correspond à un seul public, ce qui rend la configuration de sécurité lisible.

| Groupe | Préfixe principal | Rôle | Endpoints | Section |
| --- | --- | --- | --- | --- |
| Authentification et compte | `/auth`, `/me` | Public, connecté | 6 | 2 |
| Offres publiques et référentiels | `/jobs`, `/categories`, `/skills` | Public, connecté | 5 | 3 |
| Profil candidat et CV | `/candidates/me` | CANDIDATE | 15 | 4 |
| Candidatures côté candidat | `/jobs/{id}/applications`, `/candidates/me` | CANDIDATE | 6 | 5 |
| Offres côté recruteur | `/jobs`, `/company/jobs` | RECRUITER | 10 | 6 |
| Candidatures côté recruteur | `/applications` | RECRUITER | 9 | 7 |
| Entretiens | `/applications/{id}/interviews`, `/interviews` | RECRUITER | 6 | 8 |
| IA | `/applications/{id}/ai`, `/ai/tasks` | RECRUITER | 5 | 9 |
| Tableau de bord recruteur et administration | `/company/dashboard`, `/admin` | RECRUITER, ADMIN | 24 | 10 |
| **Total** |  |  | **86** |  |

Une même donnée peut avoir deux vues : une candidature vue par le candidat (`/candidates/me/applications/{id}`) et par le recruteur (`/applications/{id}`). Ce sont deux DTO distincts ; le DTO candidat ne contient tout simplement pas les champs internes, ce qui empêche toute fuite par oubli. La spécification OpenAPI générée par springdoc reprendra exactement ce découpage.

## 2. Authentification et compte

Six endpoints ; seuls `register` et `login` reçoivent des identifiants, et le refresh token ne transite jamais dans un corps JSON.

| Méthode | Chemin | Rôle | Succès | Erreurs propres | Règles | UC |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/auth/register` | Public | 201 + cookie | 409 EMAIL\_ALREADY\_USED | Rôle forcé à CANDIDATE, profil vide créé (RG-01, RG-04) | UC-03 |
| POST | `/auth/login` | Public | 200 + cookie | 401 AUTH\_INVALID\_CREDENTIALS, 429 RATE\_LIMITED | Message identique pour mot de passe faux et compte désactivé ; audité | UC-04 |
| POST | `/auth/refresh` | Public (cookie) | 200 + nouveau cookie | 401 AUTH\_REFRESH\_INVALID | Rotation ; un jeton **déjà tourné** présenté de nouveau = vol présumé, révocation de toutes les sessions et audit ; un jeton révoqué pour une autre cause (déconnexion, changement de mot de passe, compte désactivé) = simple 401 | UC-05 |
| POST | `/auth/logout` | Public (cookie) | 204 | — | Idempotent : efface toujours le cookie | UC-06 |
| POST | `/auth/password` | Connecté | 204 | 422 INVALID\_CURRENT\_PASSWORD | Révoque les autres sessions ; lève `mustChangePassword` | UC-07 |
| GET | `/me` | Connecté | 200 | — | Accessible même si le mot de passe doit être changé | — |

### POST /auth/register

```json
{
  "email": "salma.idrissi@example.com",
  "password": "une-phrase-de-passe-longue",
  "firstName": "Salma",
  "lastName": "Idrissi",
  "privacyAccepted": true
}
```

Validation : email valide de 254 caractères maximum ; mot de passe de 12 à 128 caractères, absent de la liste des mots de passe courants ; noms de 1 à 80 caractères ; `privacyAccepted` doit valoir `true`.

### Réponse de register, login et refresh

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 900,
  "user": {
    "id": "0192a7c4-5b1e-7c3d-9f20-6a1b2c3d4e5f",
    "email": "salma.idrissi@example.com",
    "firstName": "Salma",
    "lastName": "Idrissi",
    "role": "CANDIDATE",
    "companyId": null,
    "mustChangePassword": false
  }
}
```

En-tête associé : `Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800`. `GET /me` renvoie le seul objet `user`.

### POST /auth/password

Corps : `{ "currentPassword": "...", "newPassword": "..." }`. Tant que `mustChangePassword` vaut `true`, tous les autres endpoints protégés répondent `403 PASSWORD_CHANGE_REQUIRED`, sauf `/me`, `/auth/password` et `/auth/logout`.

### Protection contre la falsification de requête (CSRF)

`/auth/refresh` et `/auth/logout` s'appuient sur un cookie, donc pourraient être déclenchés depuis un autre site. Trois protections cumulées : `SameSite=Strict` (le navigateur n'envoie pas le cookie depuis un autre site), un chemin limité à `/api/v1/auth`, et une réponse qui contient le jeton dans le corps, illisible pour un site tiers. Les autres endpoints n'utilisent pas de cookie et ne sont pas exposés au CSRF.

**Code ajouté au catalogue** : `INVALID_CURRENT_PASSWORD` (422), ancien mot de passe incorrect lors d'un changement.

## 3. Offres publiques et référentiels

Cinq endpoints de lecture, dont trois publics ; `POST /skills` est le seul à écrire, et il ne crée jamais de doublon.

| Méthode | Chemin | Rôle | Succès | Erreurs propres | Règles | UC |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/jobs` | Public | 200 page | — | Seules les offres PUBLISHED non expirées (RG-11, RG-19) | UC-01 |
| GET | `/jobs/{id}` | Public | 200 | 404 si non publiée ou expirée | Champ `alreadyApplied` rempli si un candidat est connecté | UC-02 |
| GET | `/categories` | Public | 200 liste | — | Catégories actives, triées par nom | UC-01 |
| GET | `/skills?q=spr&size=20` | Connecté | 200 liste | — | Compétences actives dont le nom ou un alias commence par `q` ; 20 au maximum | UC-11, UC-20 |
| POST | `/skills` | C, R | 201 ou 200 | — | « Obtenir ou créer » : si la forme normalisée existe déjà (nom ou alias), renvoie l'existante en 200 | UC-11, UC-20 |

### GET /jobs — paramètres

| Paramètre | Type | Exemple | Effet |
| --- | --- | --- | --- |
| q | texte | `développeur java` | Recherche plein texte sur titre et description, sans accents ni casse |
| city, countryCode | texte | `Casablanca`, `MA` | Égalité, sans casse |
| workMode | liste | `REMOTE,HYBRID` | L'une des valeurs |
| contractType | liste | `CDI` | L'une des valeurs |
| experienceLevel | liste | `MID,SENIOR` | L'une des valeurs |
| categoryId | UUID |  | Égalité |
| skillId | liste d'UUID |  | L'offre exige ou souhaite toutes ces compétences |
| salaryMin | nombre | `15000` | Salaire maximum de l'offre ≥ valeur ; ignore les offres sans salaire public |
| sort | texte | `publishedAt,desc` | Liste blanche : `publishedAt`, `salaryMax` ; défaut `publishedAt,desc` |

### GET /jobs — élément de la page

```json
{
  "id": "0192a7c4-...",
  "title": "Développeur backend Java",
  "company": { "id": "0192a6f1-...", "name": "Atlas Digital" },
  "location": { "city": "Casablanca", "countryCode": "MA" },
  "workMode": "HYBRID",
  "contractType": "CDI",
  "experienceLevel": "MID",
  "salary": { "min": 15000, "max": 22000, "currency": "MAD", "period": "MONTHLY" },
  "topSkills": ["Java", "Spring Boot", "PostgreSQL"],
  "publishedAt": "2026-10-12T09:00:00Z",
  "expiresAt": "2026-12-12T23:59:59Z"
}
```

`salary` vaut `null` si le recruteur ne l'a pas rendu public. `topSkills` contient au plus 5 compétences requises.

### GET /jobs/{id} — champs en plus

Phase 6 : pour un candidat connecté, `myApplicationId` donne l'identifiant de sa candidature (le bouton « Postuler » devient « Suivre ma candidature »).

`description`, `category { id, name }`, `minYearsExperience`, `requiredSkills [{ id, name, minLevel }]`, `preferredSkills [{ id, name }]` et `alreadyApplied` (booléen pour un candidat connecté, `null` sinon). L'endpoint est public mais lit le token s'il est présent.

### POST /skills

Corps : `{ "name": "Spring Boot" }` (2 à 80 caractères : lettres, chiffres, espaces et `+ # . / & ( ) ' -`, pour C#, C++, .NET, CI/CD). Réponse : `{ "id": "...", "name": "Spring Boot" }`. La forme normalisée (minuscules, espaces réduits) est comparée aux noms puis aux alias ; deux créations simultanées du même nom donnent une seule compétence. L'admin peut ensuite fusionner les doublons qui auraient échappé à la normalisation.

## 4. Profil candidat et CV

Quinze endpoints sous `/candidates/me`, réservés au rôle CANDIDATE : l'identité vient du token, jamais de l'URL, ce qui rend impossible par construction l'accès au profil d'un autre candidat.

| Méthode | Chemin | Succès | Erreurs propres | Règles | UC |
| --- | --- | --- | --- | --- | --- |
| GET | `/candidates/me/profile` | 200 | — | Profil complet, CV actif, complétude | UC-10 |
| PUT | `/candidates/me/profile` | 200 | 409 CONCURRENT\_MODIFICATION | Informations de base, `version` obligatoire | UC-10 |
| POST | `/candidates/me/experiences` | 201 | — | Fin ≥ début | UC-11 |
| PUT | `/candidates/me/experiences/{id}` | 200 | 404 |  | UC-11 |
| DELETE | `/candidates/me/experiences/{id}` | 204 | 404 |  | UC-11 |
| POST | `/candidates/me/educations` | 201 | — | Année de fin ≥ année de début | UC-11 |
| PUT | `/candidates/me/educations/{id}` | 200 | 404 |  | UC-11 |
| DELETE | `/candidates/me/educations/{id}` | 204 | 404 |  | UC-11 |
| PUT | `/candidates/me/skills` | 200 profil | — | Remplace toute la liste ; 50 au maximum, sans doublon, compétences actives | UC-11 |
| PUT | `/candidates/me/languages` | 200 profil | — | Remplace toute la liste, sans doublon | UC-11 |
| PUT | `/candidates/me/links` | 200 profil | — | Remplace toute la liste ; 5 au maximum, https | UC-11 |
| GET | `/candidates/me/resume` | 200 | 404 si aucun CV actif | Métadonnées du CV actif | UC-12 |
| POST | `/candidates/me/resume` | 201 | 413 FILE\_TOO\_LARGE, 415 FILE\_INVALID\_TYPE, 422 FILE\_UNREADABLE | Remplace le CV actif (RG-07, RG-08) | UC-12 |
| GET | `/candidates/me/resume/file` | 200 PDF | 404 | `Content-Disposition: attachment` | UC-12 |
| DELETE | `/candidates/me/resume` | 204 | 404 | Effacé ou seulement désactivé s'il est joint à une candidature (RG-12) | UC-13 |

Pourquoi des listes remplacées en bloc pour compétences, langues et liens, mais des éléments individuels pour expériences et formations : les premières sont de petites listes éditées dans un seul formulaire, et un remplacement atomique évite dix appels ; les secondes contiennent de longs textes édités un par un.

### GET /candidates/me/profile

```json
{
  "firstName": "Salma",
  "lastName": "Idrissi",
  "email": "salma.idrissi@example.com",
  "phone": "+212600000000",
  "location": { "city": "Marrakech", "countryCode": "MA" },
  "headline": "Développeuse Java / Angular",
  "summary": "...",
  "yearsOfExperience": 4,
  "experiences": [{ "id": "...", "jobTitle": "Développeuse web", "companyName": "Agence X", "city": "Marrakech", "startDate": "2022-03-01", "endDate": null, "current": true, "description": "..." }],
  "educations": [{ "id": "...", "degree": "Diplôme d'ingénieur d'État", "school": "...", "fieldOfStudy": "Génie informatique", "startYear": 2017, "endYear": 2022 }],
  "skills": [{ "skillId": "...", "name": "Java", "level": "ADVANCED", "years": 2 }],
  "languages": [{ "languageCode": "fr", "level": "C1" }],
  "links": [{ "type": "GITHUB", "url": "https://github.com/..." }],
  "resume": { "id": "...", "originalFilename": "cv-salma.pdf", "sizeBytes": 182340, "uploadedAt": "2026-10-18T10:12:00Z" },
  "completeness": { "percent": 85, "missing": ["summary"] },
  "version": 3
}
```

### Corps des écritures

- **PUT profile** : `firstName`, `lastName`, `phone`, `location`, `headline`, `summary`, `yearsOfExperience`, `version`. Prénom et nom sont enregistrés sur l'utilisateur, le reste sur le profil, dans la même transaction.
- **Expérience** : `jobTitle`, `companyName`, `city`, `startDate`, `endDate` (null si en cours), `description`.
- **Formation** : `degree`, `school`, `fieldOfStudy`, `startYear`, `endYear`.
- **PUT skills** : `{ "skills": [{ "skillId": "...", "level": "ADVANCED", "years": 2 }] }` ; une compétence inconnue ou inactive donne 400 avec le champ en erreur (`skills[0].skillId`).
- **PUT languages** : `{ "languages": [{ "languageCode": "en", "level": "B2" }] }`.
- **PUT links** : `{ "links": [{ "type": "LINKEDIN", "url": "https://..." }] }`.

*Modifié en phase 4* : les listes sont enveloppées dans un objet plutôt qu'envoyées en tableau JSON nu. Les erreurs de validation gardent ainsi le chemin de l'élément fautif (`skills[3].level`), que le frontend place sous la bonne ligne, et le format pourra s'enrichir sans rupture. `PUT profile`, `PUT skills`, `PUT languages` et `PUT links` renvoient le profil complet, complétude comprise : l'écran n'a qu'une source de vérité.
- **POST resume** : `multipart/form-data`, champ unique `file`. Réponse : métadonnées comme dans `resume` ci-dessus.

Les vérifications du fichier suivent exactement la séquence 3.2 de l'analyse fonctionnelle : taille, type MIME, signature PDF, lisibilité, puis stockage sous un nom UUID.

## 5. Côté candidat : candidatures, entretiens, tableau de bord

Six endpoints ; le candidat ne voit jamais le statut interne, seulement un statut simplifié calculé par le serveur, et aucune note ni motif de rejet.

| Méthode | Chemin | Rôle | Succès | Erreurs propres | Règles | UC |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/jobs/{jobId}/applications` | C | 201 + vue candidat (détail) | 404 offre inconnue, 409 JOB\_NOT\_OPEN, 409 APPLICATION\_ALREADY\_EXISTS (avec `applicationId`), 409 CONCURRENT\_MODIFICATION (CV supprimé au même instant), 422 PROFILE\_INCOMPLETE | Corps vide ; CV actif figé (RG-09 à RG-12) | UC-14 |
| GET | `/candidates/me/applications` | C | 200 page | — | Tri `submittedAt,desc` par défaut | UC-15 |
| GET | `/candidates/me/applications/{id}` | C | 200 | 404 | Chronologie des statuts simplifiés, entretiens | UC-15 |
| POST | `/candidates/me/applications/{id}/withdraw` | C | 200 | 404, 409 INVALID\_STATUS\_TRANSITION | Corps `{ "comment": "..." }` facultatif ; annule les entretiens (RG-15, RG-22) | UC-16 |
| GET | `/candidates/me/interviews?upcoming=true` | C | 200 liste | — | Sans feedback ni participants internes ; `upcoming=true` (défaut) : prochains d'abord ; `false` : passés, plus récents d'abord ; annulés inclus | UC-17 |
| GET | `/candidates/me/dashboard` | C | 200 | — | Indicateurs de l'analyse fonctionnelle 6.3 | UC-18 |

### GET /candidates/me/dashboard (livré en phase 9, US-45)

```json
{
  "applicationsByStatus": { "SENT": 1, "IN_REVIEW": 2, "NOT_SELECTED": 1 },
  "upcomingInterviews": [
    { "id": "0192c1...", "startsAt": "2026-10-23T09:00:00Z", "durationMinutes": 45, "type": "VIDEO",
      "jobTitle": "Développeur backend Java", "companyName": "Atlas Digital" }
  ],
  "resume": { "present": true, "uploadedAt": "2026-10-01T10:00:00Z" },
  "profileCompleteness": { "percent": 80, "missing": ["SKILLS"] }
}
```

Libellés simplifiés uniquement (SCREENING et SHORTLISTED cumulés sous IN\_REVIEW) ; les statuts sans candidature sont absents ; cinq prochains entretiens SCHEDULED au plus.

### Statut simplifié : une exception assumée à la convention

Les énumérations sont normalement exposées telles quelles. Ici, la vue candidat expose un champ `candidateStatus` distinct, calculé côté serveur : SENT, IN\_REVIEW (pour SCREENING et SHORTLISTED), INTERVIEW, OFFER\_RECEIVED, HIRED, NOT\_SELECTED, WITHDRAWN. Si la correspondance était faite par le frontend, la réponse brute contiendrait SHORTLISTED, visible dans les outils du navigateur.

### Vue candidat d'une candidature

```json
{
  "id": "0192b0aa-...",
  "job": { "id": "0192a7c4-...", "title": "Développeur backend Java", "companyName": "Atlas Digital" },
  "candidateStatus": "IN_REVIEW",
  "submittedAt": "2026-10-14T08:20:00Z",
  "canWithdraw": true,
  "timeline": [
    { "candidateStatus": "SENT", "at": "2026-10-14T08:20:00Z" },
    { "candidateStatus": "IN_REVIEW", "at": "2026-10-15T11:02:00Z" }
  ],
  "interviews": []
}
```

`timeline` et `interviews` n'apparaissent que dans le détail (`interviews` reste vide jusqu'à la phase 7). Deux passages internes successifs qui donnent le même statut simplifié (SCREENING puis SHORTLISTED) n'apparaissent qu'une fois.

### 422 PROFILE\_INCOMPLETE

La réponse ajoute la liste des manques, que le frontend transforme en liens vers les bons onglets du profil :

```json
{ "status": 422, "code": "PROFILE_INCOMPLETE", "missing": ["headline", "skills", "resume"] }
```

### Entretien vu par le candidat

`{ id, jobTitle, companyName, startsAt, durationMinutes, type, meetingUrl, address, candidateInstructions, status }`.

### Tableau de bord candidat

`{ applicationsByStatus: { "IN_REVIEW": 2, "INTERVIEW": 1, ... }, upcomingInterviews: [...], resume: { present, uploadedAt }, profileCompleteness }`.

## 6. Côté recruteur : offres

Dix endpoints, tous réservés au rôle RECRUITER et limités à son entreprise ; le cycle de vie passe par cinq actions explicites plutôt que par une modification libre du statut.

| Méthode | Chemin | Succès | Erreurs propres | Règles | UC |
| --- | --- | --- | --- | --- | --- |
| GET | `/company/jobs` | 200 page | — | Tous statuts ; ARCHIVED exclues sauf si demandées ; compteurs par statut de candidature | UC-20 |
| GET | `/company/jobs/{id}` | 200 | 404 | Brouillons inclus, avec `version` | UC-20 |
| POST | `/jobs` | 201 | — | Créée en DRAFT, entreprise prise dans le token (RG-05) | UC-20 |
| PUT | `/jobs/{id}` | 200 | 404, 409 INVALID\_STATE, 409 CONCURRENT\_MODIFICATION | Modifiable en DRAFT et PUBLISHED ; auditée si PUBLISHED (RG-17) | UC-20 |
| DELETE | `/jobs/{id}` | 204 | 404, 409 RESOURCE\_IN\_USE | DRAFT uniquement (RG-18) | UC-21 |
| POST | `/jobs/{id}/publish` | 200 | 404, 409 INVALID\_STATUS\_TRANSITION, 422 JOB\_NOT\_PUBLISHABLE | Corps `{ "expiryDate": "2026-12-12" }` ; au plus 180 jours, au moins une compétence requise (RG-19, RG-20) | UC-21 |
| POST | `/jobs/{id}/unpublish` | 200 | 404, 409 INVALID\_STATUS\_TRANSITION | Retour en DRAFT si aucune candidature | UC-21 |
| POST | `/jobs/{id}/close` | 200 | 404, 409 INVALID\_STATUS\_TRANSITION | Les candidatures en cours continuent | UC-21 |
| POST | `/jobs/{id}/reopen` | 200 | 404, 409 INVALID\_STATUS\_TRANSITION, 422 JOB\_NOT\_PUBLISHABLE | Corps `{ "expiryDate": "..." }` | UC-21 |
| POST | `/jobs/{id}/archive` | 200 | 404, 409 INVALID\_STATUS\_TRANSITION | Depuis CLOSED uniquement | UC-21 |

### Corps de POST /jobs et PUT /jobs/{id}

```json
{
  "title": "Développeur backend Java",
  "description": "Nous recherchons...",
  "categoryId": "0192a601-...",
  "location": { "city": "Casablanca", "countryCode": "MA" },
  "workMode": "HYBRID",
  "contractType": "CDI",
  "experienceLevel": "MID",
  "minYearsExperience": 3,
  "salary": { "min": 15000, "max": 22000, "currency": "MAD", "period": "MONTHLY", "visible": true },
  "requiredSkills": [{ "skillId": "...", "minLevel": "ADVANCED" }],
  "preferredSkills": [{ "skillId": "..." }],
  "version": 0
}
```

`version` est absent à la création et obligatoire en modification. `salary` est facultatif. Une compétence ne peut figurer qu'une fois, dans l'une des deux listes.

### Réponse (vue recruteur)

Tous les champs ci-dessus, plus `id`, `status`, `publishedAt`, `expiresAt`, `createdBy { id, name }`, `applicationCounts { "SUBMITTED": 4, "SCREENING": 2, ... }` et `version`.

### 422 JOB\_NOT\_PUBLISHABLE

La réponse liste les raisons : `{ "code": "JOB_NOT_PUBLISHABLE", "reasons": ["EXPIRY_TOO_FAR", "NO_REQUIRED_SKILL"] }`.

## 7. Côté recruteur : candidatures

Neuf endpoints, réservés au rôle RECRUITER et limités aux offres de son entreprise ; le serveur indique lui-même au frontend quelles transitions sont permises.

| Méthode | Chemin | Succès | Erreurs propres | Règles | UC |
| --- | --- | --- | --- | --- | --- |
| GET | `/applications` | 200 page | — | Filtres ci-dessous ; jamais triée par score par défaut (RG-25) | UC-22 |
| GET | `/applications/{id}` | 200 | 404 | Dossier complet, `allowedTransitions` calculées | UC-23 |
| GET | `/applications/{id}/resume` | 200 PDF | 404 | CV figé à l'envoi ; téléchargement audité | UC-23 |
| PATCH | `/applications/{id}/status` | 200 | 404, 409 INVALID\_STATUS\_TRANSITION, 409 OFFER\_REQUIRES\_COMPLETED\_INTERVIEW, 409 CONCURRENT\_MODIFICATION, 422 REJECTION\_REASON\_REQUIRED | WITHDRAWN interdit ici (réservé au candidat) ; annulation des entretiens si REJECTED | UC-24 |
| GET | `/applications/{id}/history` | 200 liste | 404 | Plus récent d'abord | UC-23 |
| GET | `/applications/{id}/notes` | 200 liste | 404 | Notes non supprimées | UC-25 |
| POST | `/applications/{id}/notes` | 201 | 404 | Corps `{ "content": "..." }`, 1 à 5 000 caractères | UC-25 |
| PUT | `/applications/{id}/notes/{noteId}` | 200 | 404, 403 ACCESS\_DENIED | Auteur uniquement | UC-25 |
| DELETE | `/applications/{id}/notes/{noteId}` | 204 | 404, 403 ACCESS\_DENIED | Auteur uniquement ; suppression logique | UC-25 |

Pourquoi 403 et non 404 pour la note d'un collègue : la note est visible de tous les recruteurs de l'entreprise, donc son existence n'est pas un secret. Le 404 reste réservé à ce qui sort du périmètre de l'appelant.

### GET /applications — paramètres

| Paramètre | Type | Effet |
| --- | --- | --- |
| jobOfferId | UUID | Candidatures d'une offre |
| status | liste | `SCREENING,SHORTLISTED` |
| skillId | liste d'UUID | Le candidat déclare toutes ces compétences |
| submittedFrom, submittedTo | date | Période de dépôt |
| candidateName | texte | Nom ou prénom contient, sans casse |
| minScore | entier 0 à 100 | Dernier score de matching ≥ valeur ; exclut les candidatures jamais analysées |
| sort | texte | Liste blanche : `submittedAt` (défaut, desc), `lastMatchScore` (seulement si demandé) |

Élément de la page : `{ id, candidate { id, fullName, headline }, job { id, title }, status, submittedAt, lastMatchScore }`.

Précisions de la phase 6 : `submittedFrom` et `submittedTo` sont des jours calendaires dans le fuseau de l'entreprise, `submittedTo` inclus ; `candidateName` ignore casse et accents et accepte « nom prénom » ; `minScore` hors de 0 à 100 → 400 VALIDATION\_FAILED. Implémentation : requête SQL de lecture qui joint les tables d'autres modules, exception documentée dans US-30.

### GET /applications/{id}

```json
{
  "id": "0192b0aa-...",
  "status": "SHORTLISTED",
  "allowedTransitions": ["INTERVIEW", "REJECTED"],
  "submittedAt": "2026-10-14T08:20:00Z",
  "job": { "id": "...", "title": "Développeur backend Java" },
  "candidate": { "id": "...", "fullName": "Salma Idrissi", "email": "...", "phone": "...", "headline": "...", "experiences": [], "educations": [], "skills": [], "languages": [], "links": [] },
  "resume": { "id": "...", "originalFilename": "cv-salma.pdf", "uploadedAt": "..." },
  "rejection": null,
  "interviews": [{ "id": "...", "startsAt": "...", "type": "VIDEO", "status": "SCHEDULED" }],
  "notesCount": 2,
  "ai": { "lastMatchScore": 81, "lastAnalysisAt": "2026-10-15T09:00:00Z" },
  "version": 4
}
```

`allowedTransitions` vient de `ApplicationStatus.allowedTargets()` : le frontend n'affiche que ces choix, sans dupliquer la règle. `INTERVIEW → OFFER` n'y figure que si un entretien est terminé.

Champs ajoutés en phase 6 : `statusChangedAt`, `withdrawalComment` (message du candidat qui se retire) ; `resume` porte aussi `sizeBytes`. `interviews`, `notesCount` et `ai.lastAnalysisAt` restent vides jusqu'aux phases 7 et 8.

### PATCH /applications/{id}/status

```json
{
  "targetStatus": "REJECTED",
  "rejectionReason": "INSUFFICIENT_EXPERIENCE",
  "comment": "Profil intéressant mais trop junior pour ce poste.",
  "version": 4
}
```

`rejectionReason` est obligatoire si `targetStatus` vaut REJECTED, ignoré sinon. Réponse : le dossier à jour, avec la nouvelle `version`.

### Entrée d'historique

`{ id, type, fromStatus, toStatus, actor { id, name } | null, details, occurredAt }`.

## 8. Côté recruteur : entretiens

Six endpoints ; la création est rattachée à la candidature, puis l'entretien vit comme une ressource propre, avec deux actions de fin de vie.

| Méthode | Chemin | Succès | Erreurs propres | Règles | UC |
| --- | --- | --- | --- | --- | --- |
| POST | `/applications/{id}/interviews` | 201 | 404, 409 INTERVIEW\_NOT\_ALLOWED | Candidature en INTERVIEW, date future, participants de l'entreprise (RG-21) | UC-26 |
| GET | `/interviews` | 200 page | — | Paramètres `from`, `to`, `status`, `jobOfferId`, `mine=true` ; tri `startsAt,asc` | UC-26 |
| GET | `/interviews/{id}` | 200 | 404 | Avec feedback et participants | UC-26 |
| PUT | `/interviews/{id}` | 200 | 404, 409 INVALID\_STATE, 409 CONCURRENT\_MODIFICATION | SCHEDULED uniquement ; date future ; ancienne et nouvelle date dans l'historique | UC-26 |
| POST | `/interviews/{id}/cancel` | 200 | 404, 409 INVALID\_STATE | Corps `{ "reason": "..." }` facultatif | UC-26 |
| POST | `/interviews/{id}/complete` | 200 | 404, 409 INVALID\_STATE | Après l'heure de début ; corps `{ "rating": "POSITIVE", "comment": "..." }` | UC-27 |

### Corps de création et de modification

```json
{
  "startsAt": "2026-10-22T10:00:00Z",
  "durationMinutes": 45,
  "type": "VIDEO",
  "meetingUrl": "https://meet.example.com/abc-defg",
  "address": null,
  "candidateInstructions": "Prévoir 10 minutes de présentation de votre parcours.",
  "participantIds": ["0192a9b1-..."]
}
```

En modification, ajouter `version`. `meetingUrl` est obligatoire pour VIDEO, `address` pour ON\_SITE ; un participant hors de l'entreprise donne 400 sur `participantIds`. Le frontend convertit l'heure locale saisie en UTC avant l'envoi.

Précisions de la phase 7 :

- `startsAt` accepte un décalage (`+01:00`) ; la réponse le renvoie toujours en UTC (`Z`).
- `GET /interviews` accepte aussi `applicationId` (onglet Entretiens du dossier) ; `from` et `to` sont des instants ISO.
- Réponse recruteur : `{ id, application { id, candidateName, jobOfferId, jobTitle }, startsAt, durationMinutes, type, meetingUrl, address, candidateInstructions, status, cancellationReason, feedback { rating, comment, completedAt } | null, participants [{ id, name }], scheduledBy { id, name }, version }`.
- `POST /interviews/{id}/complete` par un recruteur qui ne participe pas → 403 ACCESS\_DENIED (F-INT-04).
- Endpoint ajouté : `GET /company/recruiters` (RECRUITER) → `[{ id, name, jobTitle }]`, pour choisir les participants.
- Notes : réponse `{ id, content, author { id, name }, createdAt, updatedAt, edited, mine }` ; liste du plus ancien au plus récent.

## 9. IA

Cinq endpoints : deux lancent une tâche asynchrone, un suit la tâche, deux lisent le dernier résultat. Tous réservés au rôle RECRUITER, sur les candidatures de son entreprise.

| Méthode | Chemin | Succès | Erreurs propres | Règles | UC |
| --- | --- | --- | --- | --- | --- |
| POST | `/applications/{id}/ai/resume-analysis` | 202 | 404, 503 AI\_DISABLED | Si une tâche du même type est déjà en cours pour ce dossier, renvoie son identifiant au lieu d'en créer une | UC-28 |
| POST | `/applications/{id}/ai/matching` | 202 | 404, 503 AI\_DISABLED | Lance l'analyse du CV d'abord si nécessaire | UC-29 |
| GET | `/ai/tasks/{taskId}` | 200 | 404 | Interrogé toutes les 3 s par le frontend | UC-28, UC-29 |
| GET | `/applications/{id}/ai/resume-analysis` | 200 | 404 si jamais analysé | Dernière analyse du CV joint | UC-28 |
| GET | `/applications/{id}/ai/matching` | 200 | 404 si jamais calculé | Dernier résultat de matching | UC-29 |

La limitation de débit s'applique aux deux `POST` (10 demandes par recruteur et par minute), pour qu'une série de clics ne sature pas Ollama.

Précisions de la phase 8 :

- Endpoint ajouté : `GET /ai/status` → `{ enabled, model }`, pour que le frontend masque les boutons IA quand l'assistance est désactivée.
- Les deux `POST` répondent `202` avec la tâche et un en-tête `Location: /api/v1/ai/tasks/{id}` ; 429 RATE\_LIMITED au-delà de la limite.
- La tâche porte aussi `applicationId`. En FAILED, `FILE_UNREADABLE` signifie « CV non analysable automatiquement ».
- L'analyse renvoie `{ skills [{ name, evidence, skillId, referenceName }], experiences, educations, languages, summary, model, createdAt, disclaimer }` ; `skillId` est vide pour une compétence hors référentiel.
- Le matching ajoute `warnings` (« CV non analysable automatiquement : score calculé sur le seul profil », « Expérience non renseignée »).
- Le dossier (`GET /applications/{id}`) expose `ai: { lastMatchScore, lastMatchedAt }`.

### GET /ai/tasks/{taskId}

`{ "id": "...", "type": "MATCHING", "status": "RUNNING", "errorCode": null, "createdAt": "...", "finishedAt": null }`. En DONE, l'en-tête `Location` pointe vers le résultat ; en FAILED, `errorCode` vaut AI\_UNAVAILABLE, AI\_INVALID\_RESPONSE ou FILE\_UNREADABLE.

### GET /applications/{id}/ai/matching

```json
{
  "score": 81,
  "label": "STRONG",
  "breakdown": { "requiredCoverage": 0.90, "preferredCoverage": 0.33, "experienceFit": 1.00, "formulaVersion": "v1" },
  "matchedSkills": [
    { "name": "Java", "source": "PROFILE", "level": "ADVANCED", "required": true },
    { "name": "REST API", "source": "RESUME", "evidence": "Conception d'API REST pour...", "required": true },
    { "name": "Spring Boot", "source": "PROFILE", "level": "BEGINNER", "required": true, "belowRequiredLevel": true }
  ],
  "missingSkills": [{ "name": "Kubernetes", "required": false }, { "name": "Kafka", "required": false }],
  "strengths": ["..."],
  "weaknesses": ["..."],
  "summary": "...",
  "disclaimer": "Indicateur d'aide à la décision. Il ne remplace pas l'évaluation du recruteur.",
  "model": { "name": "...", "promptVersion": "match-explain-v1" },
  "createdAt": "2026-10-15T09:00:00Z"
}
```

`label` vaut STRONG (75 et plus), PARTIAL (50 à 74) ou WEAK (moins de 50). L'analyse de CV renvoie la même structure que `ResumeExtraction` : compétences avec extrait justificatif, expériences, formations, langues, synthèse.

## 10. Tableau de bord recruteur et administration

Vingt-sept endpoints : un tableau de bord pour le recruteur, et sous `/admin` la gestion des comptes, entreprises, référentiels et du journal d'audit (livrés en phase 10, sauf mention contraire). L'admin n'a aucun endpoint donnant accès aux CV ou aux dossiers de candidature.

| Méthode | Chemin | Rôle | Succès | Erreurs propres | Règles | UC |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/company/dashboard` | R | 200 | — | Indicateurs 6.3, périmètre de l'entreprise | UC-30 |
| GET | `/admin/dashboard` | A | 200 | — | Utilisateurs par rôle (actifs, désactivés), entreprises, offres par statut, candidatures, état de l'IA (AVAILABLE, UNAVAILABLE, DISABLED ; sonde gardée 30 s) | UC-45 |
| GET | `/admin/users` | A | 200 page | — | Filtres `role`, `companyId`, `enabled`, `q` (nom ou email, sans casse ni accents) ; tri par nom ; chaque compte avec `companyId`, `companyName`, `jobTitle` pour un recruteur | UC-40 |
| POST | `/admin/users` | A | 201 | 400 VALIDATION\_FAILED (`role`, `companyId`), 409 EMAIL\_ALREADY\_USED, 409 INVALID\_STATE (entreprise inactive) | RECRUITER ou ADMIN uniquement ; réponse `{user, temporaryPassword}` en `Cache-Control: no-store`, mot de passe renvoyé une seule fois. Livré en phase 5 (US-47) | UC-40 |
| GET | `/admin/users/{id}` | A | 200 | 404 |  | UC-40 |
| PUT | `/admin/users/{id}` | A | 200 | 400 (`companyId`), 404, 409 CONCURRENT\_MODIFICATION, 409 INVALID\_STATE (entreprise inactive) | Corps `{firstName, lastName, companyId, jobTitle, version}` ; le rôle n'est pas modifiable (G-3), un `role` envoyé est ignoré ; changer l'entreprise d'un recruteur révoque ses sessions | UC-40 |
| POST | `/admin/users/{id}/disable` | A | 200 | 404, 409 LAST\_ADMIN | Révoque les refresh tokens et invalide les access tokens dès la requête suivante (version de session) ; idempotent (RG-03, RG-29) | UC-40 |
| POST | `/admin/users/{id}/enable` | A | 200 | 404, 409 INVALID\_STATE | Refusé pour un recruteur dont l'entreprise est désactivée | UC-40 |
| GET | `/admin/companies` | A | 200 page | — | Filtres `active`, `q` (nom) ; tri par nom ; avec `recruiterCount`, `jobCount` (non archivées), `publishedJobCount`. Liste complète non paginée en phase 5, paginée depuis la phase 10 | UC-41 |
| POST | `/admin/companies` | A | 201 | 409 RESOURCE\_IN\_USE (nom déjà pris) | Livré en phase 5 ; audité depuis la phase 10 | UC-41 |
| GET | `/admin/companies/{id}` | A | 200 | 404 | Avec nombre de recruteurs et d'offres | UC-41 |
| PUT | `/admin/companies/{id}` | A | 200 | 400 (`version` manquante), 404, 409 CONCURRENT\_MODIFICATION, 409 RESOURCE\_IN\_USE | Mêmes champs que la création, plus `version` | UC-41 |
| POST | `/admin/companies/{id}/deactivate` | A | 200 | 404 | Une transaction : clôt les offres publiées, désactive les recruteurs et révoque leurs sessions (RG-31). Réponse `{company, closedJobs, disabledRecruiters}` ; rejouer ne change rien | UC-41 |
| POST | `/admin/companies/{id}/activate` | A | 200 | 404 | Ne réactive pas les recruteurs automatiquement | UC-41 |
| GET | `/admin/categories` | A | 200 liste | — | Actives et inactives, triées par nom, avec `offerCount` | UC-42 |
| POST | `/admin/categories` | A | 201 | 409 RESOURCE\_IN\_USE (nom déjà pris) |  | UC-42 |
| PUT | `/admin/categories/{id}` | A | 200 | 404 | Renommage | UC-42 |
| POST | `/admin/categories/{id}/deactivate` | A | 200 | 404 | Plus proposée pour les nouvelles offres | UC-42 |
| POST | `/admin/categories/{id}/activate` | A | 200 | 404 | Ajouté en phase 10 : annuler une désactivation | UC-42 |
| DELETE | `/admin/categories/{id}` | A | 204 | 404, 409 RESOURCE\_IN\_USE (avec `usageCount`) | Seulement si aucune offre ne l'utilise (RG-30) ; le frontend propose alors la désactivation | UC-42 |
| GET | `/admin/skills` | A | 200 page | — | Filtres `q` (nom ou alias), `active` ; avec `usageCount` (profils et offres) et `aliases` | UC-43 |
| PUT | `/admin/skills/{id}` | A | 200 | 404, 409 RESOURCE\_IN\_USE (nom ou alias d'une autre compétence, avec `skillId`) | Renommage ; l'ancien nom devient un alias | UC-43 |
| POST | `/admin/skills/{id}/merge` | A | 200 | 400 (`targetSkillId` = source), 404, 409 INVALID\_STATE (cible désactivée) | Corps `{ "targetSkillId": "..." }` ; références reportées (un exemplaire par profil ou offre, exigence la plus forte), alias créé, source supprimée, audité. Réponse `{target, references}`. *Écart : 400 plutôt que 422, c'est une erreur de saisie* | UC-43 |
| POST | `/admin/skills/{id}/deactivate` | A | 200 | 404 | Plus proposée à l'autocomplétion ; les profils et offres la gardent | UC-43 |
| POST | `/admin/skills/{id}/activate` | A | 200 | 404 | Ajouté en phase 10 | UC-43 |
| GET | `/admin/audit-logs` | A | 200 page | 400 (`to` ≤ `from`) | Filtres `from` (inclus), `to` (exclu) en ISO 8601, `actorId`, `action`, `result` ; 50 par page, plus récentes d'abord ; lecture seule (déclencheur en base) | UC-44 |
| GET | `/admin/audit-logs/actions` | A | 200 liste | — | Ajouté en phase 10 : actions présentes dans le journal, pour le filtre | UC-44 |

### GET /company/dashboard (livré en phase 9, US-44)

```json
{
  "jobsByStatus": { "DRAFT": 1, "PUBLISHED": 3, "CLOSED": 2 },
  "applicationsReceived": { "last7Days": 12, "last30Days": 40 },
  "inScreening": 5,
  "shortlisted": 2,
  "upcomingInterviews": [
    { "id": "0192c1...", "applicationId": "0192b0...", "startsAt": "2026-10-23T09:00:00Z", "type": "VIDEO",
      "candidateName": "Salma Idrissi", "jobTitle": "Développeur backend Java" }
  ],
  "hires": { "last30Days": 1 },
  "perJob": [
    { "jobId": "0192a7...", "title": "Développeur backend Java", "status": "PUBLISHED",
      "countsByStatus": { "SUBMITTED": 4, "SCREENING": 2 } }
  ]
}
```

Périmètre : l'entreprise du recruteur connecté (lue dans le jeton). Périodes glissantes de 7 et 30 × 24 h ; embauches lues dans l'historique ; dix prochains entretiens SCHEDULED sur 7 jours ; tableau limité aux 50 offres non archivées les plus récentes. 15 ms mesurées sur 10 000 candidatures (US-44).

### POST /admin/users

Corps : `{ "email", "firstName", "lastName", "role": "RECRUITER", "companyId", "jobTitle" }` (`companyId` obligatoire pour un recruteur, interdit pour un admin). Réponse 201 :

```json
{
  "user": { "id": "...", "email": "...", "role": "RECRUITER", "companyId": "...", "enabled": true, "mustChangePassword": true },
  "temporaryPassword": "k7Q-...-x2P"
}
```

Le mot de passe temporaire n'est jamais stocké en clair ni renvoyé une seconde fois ; s'il est perdu, l'admin désactive et recrée le compte (V2 : lien d'activation par email).

### PUT /admin/users/{id} et version de session

Désactiver un compte ou changer l'entreprise d'un recruteur incrémente sa version de session. Chaque access token porte la version de son émission (claim `sv`) ; un token d'une version dépassée reçoit `401 AUTHENTICATION_REQUIRED` dès la requête suivante, sans attendre son expiration.

### POST /admin/companies/{id}/deactivate

```json
{
  "company": { "id": "...", "name": "Atlas Digital", "active": false, "recruiterCount": 2, "jobCount": 5, "publishedJobCount": 0, "version": 4 },
  "closedJobs": 3,
  "disabledRecruiters": 2
}
```

### GET /admin/dashboard

```json
{
  "usersByRole": {
    "ADMIN": { "active": 2, "disabled": 0 },
    "CANDIDATE": { "active": 120, "disabled": 3 },
    "RECRUITER": { "active": 14, "disabled": 2 }
  },
  "companies": { "active": 6, "disabled": 1 },
  "jobsByStatus": { "ARCHIVED": 1, "CLOSED": 4, "DRAFT": 2, "PUBLISHED": 9 },
  "applications": { "total": 310, "last30Days": 42 },
  "ai": { "status": "AVAILABLE", "model": "qwen3.5:4b" }
}
```

### GET /admin/audit-logs (une entrée)

```json
{
  "id": "...", "occurredAt": "2026-10-23T09:12:44Z",
  "actorId": "...", "actorEmail": "admin@smartrecruit.ma", "actorName": "Ada Admin",
  "action": "COMPANY_DEACTIVATED", "resourceType": "COMPANY", "resourceId": "...",
  "result": "SUCCESS", "ipAddress": "10.0.0.12", "correlationId": "cf8e469a64ec85a3",
  "details": { "closedJobs": 3, "disabledRecruiters": 2 }
}
```

### Tableau de bord recruteur

```json
{
  "jobsByStatus": { "DRAFT": 1, "PUBLISHED": 3, "CLOSED": 2 },
  "applicationsReceived": { "last7Days": 12, "last30Days": 41 },
  "inScreening": 9,
  "shortlisted": 4,
  "upcomingInterviews": [{ "id": "...", "startsAt": "...", "candidateName": "...", "jobTitle": "..." }],
  "hires": { "last30Days": 1 },
  "perJob": [{ "jobId": "...", "title": "...", "countsByStatus": { "SUBMITTED": 5, "SCREENING": 3 } }]
}
```

## 11. Endpoints techniques et matrice de sécurité

La configuration Spring Security refuse tout par défaut et n'ouvre que les motifs listés ici, dans cet ordre ; le contrôle de propriété dans les services reste la seconde barrière.

### Endpoints techniques (hors `/api/v1`)

| Chemin | Accès | Profils | Usage |
| --- | --- | --- | --- |
| `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Public | Tous | Health checks Docker et supervision |
| `/actuator/metrics`, `/actuator/info` | ADMIN | Tous | Métriques et version déployée |
| `/api/docs/openapi` (JSON), `/api/docs/swagger-ui.html` | Public, lecture seule (GET) | `dev`, `demo` uniquement | Documentation OpenAPI générée par springdoc et Swagger UI ; n'existe pas en `prod` (404). Placée sous `/api` pour passer par le relais Nginx, avec sa propre CSP (US-58) |

### Règles d'accès, dans l'ordre d'évaluation

| # | Motif (relatif à `/api/v1`) | Méthode | Accès |
| --- | --- | --- | --- |
| 1 | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` | POST | Public |
| 2 | `/jobs`, `/jobs/{id}`, `/categories` | GET | Public |
| 3 | `/jobs/{id}/applications` | POST | CANDIDATE |
| 4 | `/jobs`, `/jobs/**` | POST, PUT, DELETE | RECRUITER |
| 5 | `/me`, `/auth/password` | Toutes | Connecté |
| 6 | `/skills` | GET | Connecté |
| 7 | `/skills` | POST | CANDIDATE, RECRUITER |
| 8 | `/candidates/me/**` | Toutes | CANDIDATE |
| 9 | `/company/**`, `/applications/**`, `/interviews/**`, `/ai/**` | Toutes | RECRUITER |
| 10 | `/admin/**` | Toutes | ADMIN |
| 11 | Tout le reste | Toutes | Refusé |

L'ordre compte : la règle 3 doit précéder la règle 4, sinon `POST /jobs/{id}/applications` serait réservé aux recruteurs. Un test d'intégration vérifie chaque ligne pour chaque rôle (US-10).

### Deux barrières

| Barrière | Où | Répond à la question | Exemple d'échec |
| --- | --- | --- | --- |
| Rôle | Règles ci-dessus, avant le contrôleur | « Ce type d'utilisateur peut-il appeler cet endpoint ? » | Un candidat appelle `/applications` → 403 |
| Propriété | Service applicatif, avec `CurrentUser` | « Cette ressource précise lui appartient-elle ? » | Un recruteur de l'entreprise A ouvre une candidature de B → 404 |

La première barrière seule laisserait passer l'accès aux données d'une autre entreprise : c'est la faille la plus fréquente des API (BOLA).

## 12. Écarts par rapport aux exemples du prompt

Tes exemples d'endpoints sont presque tous conservés ; quatre changent, chacun pour une raison de sécurité ou de cohérence.

| Exemple du prompt | Dans cette spécification | Raison |
| --- | --- | --- |
| `/api/...` | `/api/v1/...` | Versionner dès le départ permet d'introduire une v2 sans casser les clients existants |
| `POST /api/candidates/{id}/resume` | `POST /candidates/me/resume` | L'identité vient du token : aucun identifiant à falsifier, l'IDOR est impossible par construction |
| `POST /api/ai/resume-analysis`, `POST /api/ai/matching` | `POST /applications/{id}/ai/resume-analysis`, `.../ai/matching` → 202 + tâche | Le traitement est asynchrone (G-7) ; la candidature donne le contexte d'autorisation et désigne sans ambiguïté le CV et l'offre |
| `GET /api/applications` pour tous | `/applications` pour le recruteur, `/candidates/me/applications` pour le candidat | Deux publics, deux vues, deux DTO |
| `POST/PUT/DELETE /api/jobs`, `POST /api/jobs/{id}/applications`, `PATCH /api/applications/{id}/status`, `POST /api/auth/register`, `/login`, `/refresh` | Conservés tels quels (sous `/api/v1`) | — |

### Codes d'erreur ajoutés au catalogue

| Code | HTTP | Déclencheur |
| --- | --- | --- |
| INVALID\_CURRENT\_PASSWORD | 422 | Ancien mot de passe incorrect lors d'un changement |
| INVALID\_STATE | 409 | Action impossible dans l'état actuel, hors changement de statut (modifier une offre CLOSED, clôturer un entretien non commencé) |
| JOB\_NOT\_PUBLISHABLE | 422 | Publication refusée ; la réponse liste les raisons |

Ils correspondent à l'`InvalidStateException` et aux `BusinessRuleViolationException` du diagramme de classes ; le catalogue de l'analyse fonctionnelle sera complété en conséquence.

## 13. Ce que tu dois savoir défendre

**« PUT ou PATCH ? »** PUT remplace la ressource entière avec ce que le client envoie ; PATCH modifie une partie. J'utilise PUT pour les formulaires complets (offre, profil) et PATCH pour le seul changement de statut, car le client n'envoie qu'une cible et un motif.

**« `POST /jobs/{id}/publish`, ce n'est pas très REST ? »** Une transition d'état est une commande avec ses propres règles, pas une simple écriture de champ. L'exposer comme action rend chaque règle testable et documentée séparément, et évite qu'un `PUT` puisse changer le statut en contournant les vérifications. C'est un compromis pragmatique courant, y compris dans de grandes API publiques.

**« Quelles méthodes sont idempotentes ? »** GET, PUT et DELETE : les répéter donne le même état. POST ne l'est pas, sauf quand on le rend tel : `logout` l'est, `POST /skills` aussi (obtenir ou créer), et la candidature est protégée par la contrainte d'unicité, donc un double clic ne crée pas deux candidatures.

**« 401, 403 ou 404 ? »** 401 : on ne sait pas qui vous êtes (token absent ou expiré). 403 : on sait qui vous êtes, mais votre rôle n'autorise pas l'action. 404 : la ressource n'existe pas, ou elle existe mais hors de votre périmètre ; on ne le précise pas, pour ne pas révéler son existence.

**« Pourquoi 202 pour l'IA ? »** 202 signifie « demande acceptée, traitement en cours ». Le client reçoit immédiatement un identifiant de tâche et interroge son état ; aucune connexion HTTP ne reste ouverte pendant une minute.

**« Pagination par numéro de page ou par curseur ? »** Par page ici : les listes sont modestes, l'utilisateur veut sauter à la page 3, et Spring Data la fournit directement. Le curseur devient préférable pour de très grands volumes ou un défilement infini, car il ne ralentit pas sur les pages lointaines.

**« Comment gères-tu deux modifications simultanées ? »** Le client renvoie la `version` qu'il a lue. Si quelqu'un a modifié la ressource entre-temps, la version a changé et le serveur répond 409 au lieu d'écraser silencieusement la modification de l'autre.
