-- Génère 2 000 offres publiées fictives pour mesurer la recherche (phase 5, critère de US-25).
-- Usage : psql -h localhost -U smartrecruit -d smartrecruit -f scripts/perf/seed-2000-jobs.sql
-- À lancer sur une base de développement après le démarrage de l'API (migrations appliquées).
-- Réexécutable : supprime d'abord l'entreprise de mesure et ses offres.

BEGIN;

DELETE FROM job_offer WHERE company_id IN (SELECT id FROM company WHERE name = 'Mesure Performance');
DELETE FROM recruiter_profile WHERE company_id IN (SELECT id FROM company WHERE name = 'Mesure Performance');
DELETE FROM company WHERE name = 'Mesure Performance';
DELETE FROM app_user WHERE email = 'perf-recruteur@smartrecruit.local';

INSERT INTO company (name, city, country_code, created_at, updated_at)
VALUES ('Mesure Performance', 'Casablanca', 'MA', now(), now());

-- Compte inutilisable pour se connecter (hash invalide) : seulement auteur des offres
INSERT INTO app_user (email, password_hash, first_name, last_name, role, must_change_password, created_at, updated_at)
VALUES ('perf-recruteur@smartrecruit.local', '{noop}!', 'Perf', 'Recruteur', 'RECRUITER', TRUE, now(), now());

INSERT INTO recruiter_profile (user_id, company_id, created_at, updated_at)
SELECT u.id, c.id, now(), now()
FROM app_user u, company c
WHERE u.email = 'perf-recruteur@smartrecruit.local' AND c.name = 'Mesure Performance';

WITH params AS (
    SELECT
        ARRAY['Développeur backend Java', 'Développeuse Angular', 'Ingénieur DevOps', 'Data analyst',
              'Chef de projet digital', 'Responsable ressources humaines', 'Comptable confirmé',
              'Commercial grands comptes', 'Designer UX/UI', 'Administrateur systèmes Linux',
              'Développeur full stack', 'Ingénieur cybersécurité', 'Technicien support', 'Data scientist',
              'Product owner', 'Chargé de marketing digital', 'Contrôleur de gestion', 'Développeur mobile',
              'Architecte logiciel', 'Testeur QA'] AS titles,
        ARRAY['Casablanca', 'Rabat', 'Marrakech', 'Tanger', 'Fès', 'Agadir', 'Meknès', 'Oujda'] AS cities,
        ARRAY['ON_SITE', 'HYBRID', 'REMOTE'] AS modes,
        ARRAY['CDI', 'CDD', 'INTERNSHIP', 'FREELANCE', 'APPRENTICESHIP'] AS contracts,
        ARRAY['JUNIOR', 'MID', 'SENIOR', 'LEAD'] AS levels,
        (SELECT id FROM company WHERE name = 'Mesure Performance') AS company_id,
        (SELECT id FROM app_user WHERE email = 'perf-recruteur@smartrecruit.local') AS recruiter_id,
        (SELECT array_agg(id) FROM job_category WHERE active) AS categories
)
INSERT INTO job_offer (company_id, category_id, created_by, title, description, city, country_code, work_mode,
                       contract_type, experience_level, min_years_experience, salary_min, salary_max,
                       salary_currency, salary_period, salary_visible, status, published_at, expires_at,
                       created_at, updated_at)
SELECT p.company_id,
       p.categories[1 + (n % array_length(p.categories, 1))],
       p.recruiter_id,
       p.titles[1 + (n % 20)] || ' #' || n,
       'Au sein d''une équipe produit, vous participerez à la conception, au développement et à '
           || 'l''amélioration continue de nos services. Poste ' || p.titles[1 + (n % 20)]
           || ' avec un accompagnement et des projets variés pour nos clients au Maroc et en Europe.',
       p.cities[1 + (n % 8)], 'MA',
       p.modes[1 + (n % 3)], p.contracts[1 + (n % 5)], p.levels[1 + (n % 4)], n % 10,
       8000 + (n % 20) * 1000, 12000 + (n % 20) * 1500, 'MAD', 'MONTHLY', n % 3 <> 0,
       'PUBLISHED', now() - (n % 60) * interval '1 day', now() + (30 + n % 90) * interval '1 day',
       now(), now()
FROM params p, generate_series(1, 2000) AS n;

-- Une compétence requise et une souhaitée par offre, tirées du référentiel
WITH skills AS (SELECT array_agg(id ORDER BY normalized_name) AS ids FROM skill WHERE active),
     offers AS (SELECT id, row_number() OVER (ORDER BY id) AS n FROM job_offer
                WHERE company_id = (SELECT id FROM company WHERE name = 'Mesure Performance'))
INSERT INTO job_offer_skill (job_offer_id, skill_id, requirement, min_level)
SELECT o.id, s.ids[1 + (o.n % array_length(s.ids, 1))], 'REQUIRED', 'INTERMEDIATE' FROM offers o, skills s
UNION ALL
SELECT o.id, s.ids[1 + ((o.n + 7) % array_length(s.ids, 1))], 'PREFERRED', NULL FROM offers o, skills s;

COMMIT;

ANALYZE job_offer;
ANALYZE job_offer_skill;
