-- Volume de démonstration pour mesurer les tableaux de bord (phase 9, critère de US-44) :
-- 2 000 candidats, 10 000 candidatures sur les offres de « Mesure Performance » (seed-2000-jobs.sql),
-- leur historique et 300 entretiens à venir.
-- Usage : psql -h localhost -U smartrecruit -d smartrecruit -f scripts/perf/seed-applications.sql
-- Prérequis : seed-2000-jobs.sql déjà appliqué, migrations jusqu'à V13. Réexécutable.

BEGIN;

DELETE FROM interview_participant WHERE interview_id IN (SELECT id FROM interview WHERE company_id IN
    (SELECT id FROM company WHERE name = 'Mesure Performance'));
DELETE FROM interview WHERE company_id IN (SELECT id FROM company WHERE name = 'Mesure Performance');
ALTER TABLE application_event DISABLE TRIGGER trg_application_event_immutable;   -- nettoyage du jeu de mesure seulement
DELETE FROM application_event WHERE application_id IN (SELECT a.id FROM application a JOIN app_user u
    ON u.id = a.candidate_id WHERE u.email LIKE 'perf-candidat-%');
ALTER TABLE application_event ENABLE TRIGGER trg_application_event_immutable;
DELETE FROM application WHERE candidate_id IN (SELECT id FROM app_user WHERE email LIKE 'perf-candidat-%');
DELETE FROM resume WHERE candidate_id IN (SELECT id FROM app_user WHERE email LIKE 'perf-candidat-%');
DELETE FROM candidate_profile WHERE user_id IN (SELECT id FROM app_user WHERE email LIKE 'perf-candidat-%');
DELETE FROM app_user WHERE email LIKE 'perf-candidat-%';

INSERT INTO app_user (email, password_hash, first_name, last_name, role, privacy_accepted_at, created_at, updated_at)
SELECT 'perf-candidat-' || g || '@smartrecruit.local', '{noop}!', 'Candidat', 'N' || g, 'CANDIDATE', now(), now(), now()
FROM generate_series(1, 2000) g;

INSERT INTO candidate_profile (user_id, headline, years_of_experience, created_at, updated_at)
SELECT id, 'Profil de mesure', 3, now(), now() FROM app_user WHERE email LIKE 'perf-candidat-%';

INSERT INTO resume (candidate_id, storage_key, original_filename, content_type, size_bytes, sha256, active, uploaded_at)
SELECT id, 'perf-' || id || '.pdf', 'cv.pdf', 'application/pdf', 1000, repeat('0', 64), TRUE, now()
FROM app_user WHERE email LIKE 'perf-candidat-%';

-- 5 candidatures par candidat, sur des offres différentes, déposées au cours des 90 derniers jours
WITH candidates AS (
    SELECT u.id, r.id AS resume_id, row_number() OVER (ORDER BY u.id) AS n
    FROM app_user u JOIN resume r ON r.candidate_id = u.id WHERE u.email LIKE 'perf-candidat-%'
), jobs AS (
    SELECT j.id, row_number() OVER (ORDER BY j.id) AS n, count(*) OVER () AS total
    FROM job_offer j JOIN company c ON c.id = j.company_id WHERE c.name = 'Mesure Performance'
)
INSERT INTO application (job_offer_id, candidate_id, resume_id, status, rejection_reason, submitted_at,
                         status_changed_at, created_at, updated_at)
SELECT j.id, c.id, c.resume_id,
       (ARRAY['SUBMITTED', 'SCREENING', 'SHORTLISTED', 'INTERVIEW', 'REJECTED', 'WITHDRAWN'])[1 + (c.n + k) % 6],
       CASE WHEN (c.n + k) % 6 = 4 THEN 'PROFILE_MISMATCH' END,
       now() - ((c.n * 7 + k * 13) % 90) * interval '1 day',
       now(), now(), now()
FROM candidates c CROSS JOIN generate_series(0, 4) k
JOIN jobs j ON j.n = 1 + (c.n * 5 + k) % j.total;

INSERT INTO application_event (application_id, event_type, to_status, actor_id, occurred_at)
SELECT id, 'CREATED', 'SUBMITTED', candidate_id, submitted_at FROM application
WHERE candidate_id IN (SELECT id FROM app_user WHERE email LIKE 'perf-candidat-%');

INSERT INTO application_event (application_id, event_type, from_status, to_status, occurred_at)
SELECT id, 'STATUS_CHANGED', 'SUBMITTED', status, submitted_at + interval '1 day' FROM application
WHERE status <> 'SUBMITTED' AND candidate_id IN (SELECT id FROM app_user WHERE email LIKE 'perf-candidat-%');

-- 2 % d'embauches réparties sur 90 jours, lues dans l'historique
INSERT INTO application_event (application_id, event_type, from_status, to_status, occurred_at)
SELECT id, 'STATUS_CHANGED', 'OFFER', 'HIRED', submitted_at + interval '2 days' FROM application
WHERE candidate_id IN (SELECT id FROM app_user WHERE email LIKE 'perf-candidat-%') AND random() < 0.02;

INSERT INTO interview (application_id, company_id, job_offer_id, scheduled_by, starts_at, duration_minutes, type,
                       status, created_at, updated_at)
SELECT a.id, j.company_id, a.job_offer_id, j.created_by, now() + (row_number() OVER () % 14) * interval '12 hours',
       45, 'PHONE', 'SCHEDULED', now(), now()
FROM application a JOIN job_offer j ON j.id = a.job_offer_id
WHERE a.status = 'INTERVIEW' AND a.candidate_id IN (SELECT id FROM app_user WHERE email LIKE 'perf-candidat-%')
LIMIT 300;

COMMIT;
ANALYZE;
