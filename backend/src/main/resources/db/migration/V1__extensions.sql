-- V1 : extensions PostgreSQL utilisées par le schéma.
-- Règle du projet : un script appliqué n'est jamais modifié ; toute évolution = nouveau script.

-- unaccent : recherche d'offres insensible aux accents (F-JOB-04, colonne search_vector de job_offer).
-- Extension « trusted » depuis PostgreSQL 13 : le propriétaire de la base peut la créer sans être superutilisateur.
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Note : uuidv7() (clés primaires) est natif depuis PostgreSQL 18, aucune extension n'est nécessaire.
