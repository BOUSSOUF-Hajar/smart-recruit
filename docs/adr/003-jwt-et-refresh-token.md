# ADR-003 — JWT court et refresh token en cookie HttpOnly avec rotation

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [cahier des charges G-8](../cahier-des-charges.md), [US-07](../stories/US-07.md), [US-08](../stories/US-08.md), [US-48](../stories/US-48.md)

## Contexte

L'API doit rester sans état, pour que n'importe quelle instance puisse répondre, tout en offrant une session confortable (rester connecté plusieurs jours) et résistante au vol de jeton, en particulier au XSS.

## Décision

- **Access token** : JWT signé HS256 (clé d'au moins 256 bits), valable **15 minutes**, gardé **en mémoire** côté Angular. Il porte l'identifiant, le rôle et l'entreprise de l'utilisateur.
- **Refresh token** : valeur opaque valable **7 jours**, en cookie `HttpOnly`, `Secure`, `SameSite=Strict`, limité au chemin `/api/v1/auth`. Les navigateurs acceptent un cookie `Secure` sur `http://localhost`, ce qui permet l'essai local. Seule son empreinte SHA-256 est stockée en base (`refresh_token.token_hash`).
- **Rotation** à chaque renouvellement. Un jeton déjà remplacé qui revient prouve qu'une copie circule : toutes les sessions du compte sont révoquées et l'événement `TOKEN_REUSE_DETECTED` est audité.
- Validation par le resource server de Spring Security (`NimbusJwtDecoder`), sans code cryptographique maison.

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Les deux jetons dans le `localStorage` | Lisibles par tout script injecté (XSS) |
| Session serveur (cookie `JSESSIONID`) | État partagé nécessaire dès qu'il y a plusieurs instances |
| Refresh token fixe pendant 7 jours | Un jeton volé resterait utilisable sans qu'on le remarque |
| RS256 (paire de clés) | Un seul service émet et vérifie : une clé publique n'apporte rien ici |
| Filtre JWT écrit à la main | Risque d'oublier une vérification (`exp`, `iss`, `typ`) |

## Conséquences

- **+** Un script injecté ne peut pas lire le refresh token ; l'access token ne vit que 15 minutes.
- **+** Le contrôle de propriété des services n'exige aucune requête : l'entreprise est dans le token.
- **−** Plus de pièces mobiles côté frontend : intercepteur de renouvellement, un seul renouvellement partagé, verrou Web Locks entre onglets.
- **−** Un JWT ne se révoque pas tout seul avant son expiration (voir ci-dessous).

## Ce que le développement a confirmé ou changé

- **Correctif de la phase 5 (migration V8)** : un onglet resté ouvert après une déconnexion ailleurs déclenchait à tort la révocation générale. La colonne `refresh_token.revocation_reason` distingue désormais `ROTATED` des causes légitimes (`LOGOUT`, `PASSWORD_CHANGED`, `ACCOUNT_DISABLED`…). Seul un jeton tourné qui revient est traité comme un vol ; les autres reçoivent un simple 401.
- **Révocation immédiate (US-48, migration V14)** : désactiver un compte devait couper ses sessions sans attendre 15 minutes. Chaque compte a une version de session, incrémentée à la désactivation ; le token porte la version de son émission et le validateur refuse une version dépassée. C'est le minimum d'état réintroduit, assumé.
- Code d'erreur `AUTH_TOKEN_EXPIRED` distinct de `AUTHENTICATION_REQUIRED` : un token falsifié provoque une déconnexion, jamais un renouvellement.
