package com.smartrecruit.demo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenu fictif de la démonstration. Noms et prénoms courants combinés au hasard, entreprises
 * inventées, domaines {@code .example} réservés à la documentation (RFC 2606) : aucune donnée ne
 * correspond à une personne ou une société réelle.
 */
final class DemoData {

    private DemoData() {
    }

    record Experience(String title, String company, LocalDate start, LocalDate end, String description) {
    }

    record Education(String degree, String school, String field, int endYear) {
    }

    record Candidate(String key, String firstName, String lastName, String city, String headline, String summary,
                     int years, String english, Map<String, String> skills, List<Experience> experiences,
                     Education education) {

        String email() {
            return key.equals("yasmine") ? "candidat@demo.example"
                    : (firstName + "." + lastName).toLowerCase().replace(' ', '-')
                    .replace("é", "e").replace("ï", "i") + "@demo.example";
        }

        String phone() {
            return "+212 6 00 00 " + String.format("%02d", Math.floorMod(key.hashCode(), 100)) + " 00";
        }

        /** Lignes du CV PDF : en-tête, profil, expériences, compétences, formation, langues. */
        List<String> resumeLines() {
            List<String> lines = new ArrayList<>();
            lines.add(firstName + " " + lastName);
            lines.add(headline + " - " + city + " - " + email() + " - " + phone());
            lines.add("");
            lines.add("PROFIL");
            lines.add(summary);
            lines.add("");
            lines.add("EXPERIENCE");
            for (Experience experience : experiences) {
                lines.add(experience.title() + " - " + experience.company() + " (" + experience.start().getYear() + " - "
                        + (experience.end() == null ? "aujourd'hui" : experience.end().getYear()) + ")");
                lines.add(experience.description());
            }
            lines.add("");
            lines.add("COMPETENCES");
            lines.add(String.join(", ", skills.keySet().stream().map(DemoData::display).toList()));
            lines.add("");
            lines.add("FORMATION");
            lines.add(education.degree() + " - " + education.school() + " (" + education.endYear() + ")");
            lines.add("");
            lines.add("LANGUES");
            lines.add("Arabe (langue maternelle), français (courant), anglais (" + english + ")");
            return lines;
        }
    }

    record Job(String key, String company, String title, String category, String city, String workMode,
               String contract, String level, int minYears, Integer salaryMin, Integer salaryMax,
               boolean salaryVisible, String status, int publishedDaysAgo, String description,
               Map<String, String> required, List<String> preferred) {
    }

    /** Forme affichée d'une compétence du référentiel, pour le texte du CV. */
    static String display(String normalized) {
        return switch (normalized) {
            case "java" -> "Java";
            case "spring boot" -> "Spring Boot";
            case "postgresql" -> "PostgreSQL";
            case "angular" -> "Angular";
            case "typescript" -> "TypeScript";
            case "docker" -> "Docker";
            case "kubernetes" -> "Kubernetes";
            case "ci/cd" -> "CI/CD";
            case "aws" -> "AWS";
            case "terraform" -> "Terraform";
            case "sql" -> "SQL";
            case "power bi" -> "Power BI";
            case "python" -> "Python";
            case "rest api" -> "API REST";
            case "tests unitaires" -> "Tests unitaires";
            case "git" -> "Git";
            case "excel" -> "Excel";
            case "scrum" -> "Scrum";
            case "figma" -> "Figma";
            default -> Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
        };
    }

    private static Map<String, String> skills(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static LocalDate date(int year, int month) {
        return LocalDate.of(year, month, 1);
    }

    static final List<Candidate> CANDIDATES = List.of(
            new Candidate("yasmine", "Yasmine", "El Amrani", "Casablanca", "Développeuse Java / Spring Boot",
                    "Développeuse backend, quatre ans d'expérience sur des API REST en Java et Spring Boot, "
                            + "avec PostgreSQL et Docker. Attentive à la qualité : tests, revue de code, CI.",
                    4, "B2", skills("java", "ADVANCED", "spring boot", "ADVANCED", "postgresql", "INTERMEDIATE",
                    "docker", "INTERMEDIATE", "git", "ADVANCED", "rest api", "ADVANCED", "angular", "BEGINNER"),
                    List.of(new Experience("Développeuse Java", "Société Fictive Alpha", date(2023, 1), null,
                                    "API de facturation en Spring Boot, migration vers PostgreSQL, tests d'intégration."),
                            new Experience("Développeuse junior", "Studio Exemple", date(2021, 9), date(2022, 12),
                                    "Maintenance d'une application Java EE, premières API REST.")),
                    new Education("Diplôme d'ingénieur en informatique", "École Nationale Démo", "Génie logiciel", 2021)),
            new Candidate("mehdi", "Mehdi", "Alaoui", "Rabat", "Développeur front-end Angular",
                    "Développeur front-end passionné par les interfaces accessibles, Angular et TypeScript au quotidien.",
                    3, "C1", skills("angular", "EXPERT", "typescript", "ADVANCED", "html", "ADVANCED", "css", "ADVANCED",
                    "tests unitaires", "INTERMEDIATE", "rest api", "INTERMEDIATE"),
                    List.of(new Experience("Développeur Angular", "Agence Exemple Beta", date(2022, 3), null,
                            "Refonte d'un espace client en Angular, composants accessibles, tests unitaires.")),
                    new Education("Master en informatique", "Université Démo de Rabat", "Ingénierie web", 2022)),
            new Candidate("sara", "Sara", "Benjelloun", "Casablanca", "Ingénieure Java senior",
                    "Ingénieure Java senior, sept ans sur des systèmes à fort trafic ; encadre une équipe de quatre.",
                    7, "C1", skills("java", "EXPERT", "spring boot", "EXPERT", "postgresql", "ADVANCED",
                    "kubernetes", "INTERMEDIATE", "docker", "ADVANCED", "ci/cd", "ADVANCED"),
                    List.of(new Experience("Lead développeuse Java", "Banque Fictive Gamma", date(2020, 6), null,
                                    "Architecture de microservices Spring Boot, mentorat, mise en place de la CI."),
                            new Experience("Développeuse Java", "Éditeur Exemple Delta", date(2017, 9), date(2020, 5),
                                    "Développement d'un moteur de règles, optimisation SQL.")),
                    new Education("Diplôme d'ingénieur", "École Nationale Démo", "Informatique", 2017)),
            new Candidate("anas", "Anas", "Lahlou", "Marrakech", "Ingénieur DevOps",
                    "Ingénieur DevOps : conteneurs, Kubernetes, pipelines CI/CD et infrastructure as code.",
                    6, "B2", skills("docker", "EXPERT", "kubernetes", "ADVANCED", "ci/cd", "EXPERT", "terraform",
                    "ADVANCED", "aws", "INTERMEDIATE", "linux", "EXPERT"),
                    List.of(new Experience("Ingénieur DevOps", "Hébergeur Fictif Epsilon", date(2019, 2), null,
                            "Migration vers Kubernetes, pipelines GitLab CI, Terraform sur AWS.")),
                    new Education("Licence professionnelle", "Institut Démo de Marrakech", "Systèmes et réseaux", 2018)),
            new Candidate("khadija", "Khadija", "Ouazzani", "Casablanca", "Data analyst",
                    "Analyste de données : tableaux de bord Power BI, SQL avancé, premiers modèles en Python.",
                    2, "B2", skills("sql", "ADVANCED", "power bi", "ADVANCED", "python", "INTERMEDIATE", "excel", "EXPERT"),
                    List.of(new Experience("Analyste de données", "Distributeur Exemple Zeta", date(2023, 4), null,
                            "Tableaux de bord commerciaux, automatisation des rapports mensuels.")),
                    new Education("Master", "Université Démo de Casablanca", "Statistique et data science", 2023)),
            new Candidate("rachid", "Rachid", "Naciri", "Fès", "Développeur Java junior",
                    "Jeune développeur Java, un an d'expérience, motivé pour progresser sur Spring Boot.",
                    1, "B1", skills("java", "INTERMEDIATE", "spring boot", "BEGINNER", "sql", "INTERMEDIATE", "git",
                    "INTERMEDIATE"),
                    List.of(new Experience("Développeur stagiaire", "Startup Exemple Eta", date(2024, 2), date(2024, 8),
                            "Développement d'un back-office en Java, corrections de bugs.")),
                    new Education("Licence", "Faculté Démo de Fès", "Informatique", 2024)),
            new Candidate("imane", "Imane", "Sefrioui", "Rabat", "Consultante fonctionnelle",
                    "Consultante fonctionnelle, recueil des besoins et conduite du changement sur des projets ERP.",
                    4, "C1", skills("communication", "EXPERT", "gestion de projet agile", "ADVANCED", "excel",
                    "ADVANCED", "anglais professionnel", "ADVANCED"),
                    List.of(new Experience("Consultante fonctionnelle", "Cabinet Fictif Theta", date(2021, 1), null,
                            "Ateliers métier, rédaction des spécifications, recette et formation des utilisateurs.")),
                    new Education("Master", "École Démo de Commerce", "Management des systèmes d'information", 2020)),
            new Candidate("ayoub", "Ayoub", "Mansouri", "Rabat", "Chef de projet digital",
                    "Chef de projet digital certifié Scrum, six ans de projets web et mobiles.",
                    6, "C1", skills("scrum", "EXPERT", "gestion de projet agile", "EXPERT", "communication", "ADVANCED",
                    "figma", "INTERMEDIATE"),
                    List.of(new Experience("Chef de projet", "Agence Exemple Iota", date(2019, 5), null,
                            "Pilotage de projets web, animation des cérémonies Scrum, relation client.")),
                    new Education("Master", "Université Démo de Rabat", "Management de projets", 2018)),
            new Candidate("hajar", "Hajar", "Lamrani", "Tanger", "Analyste supply chain",
                    "Analyste supply chain : prévisions, optimisation des stocks, indicateurs logistiques.",
                    3, "B2", skills("excel", "EXPERT", "power bi", "ADVANCED", "sql", "INTERMEDIATE",
                    "communication", "ADVANCED"),
                    List.of(new Experience("Analyste logistique", "Transport Fictif Kappa", date(2022, 1), null,
                            "Suivi des indicateurs de performance des entrepôts, prévisions de charge.")),
                    new Education("Diplôme d'ingénieur", "École Démo de Tanger", "Génie industriel", 2021)),
            new Candidate("youssef", "Youssef", "Bakkali", "Tanger", "Responsable d'entrepôt",
                    "Responsable d'entrepôt, encadrement d'équipes de vingt personnes, démarche d'amélioration continue.",
                    8, "B1", skills("excel", "ADVANCED", "communication", "EXPERT", "power bi", "INTERMEDIATE"),
                    List.of(new Experience("Chef d'équipe entrepôt", "Logistique Exemple Lambda", date(2017, 3), null,
                            "Organisation des flux, sécurité, réduction des erreurs de préparation.")),
                    new Education("BTS", "Lycée Démo de Tanger", "Logistique", 2015)),
            new Candidate("nora", "Nora", "Zniber", "Casablanca", "Développeuse full-stack",
                    "Développeuse full-stack Java et Angular, à l'aise du modèle de données jusqu'à l'interface.",
                    3, "C1", skills("java", "ADVANCED", "spring boot", "INTERMEDIATE", "angular", "ADVANCED",
                    "typescript", "ADVANCED", "postgresql", "INTERMEDIATE"),
                    List.of(new Experience("Développeuse full-stack", "Éditeur Fictif Mu", date(2022, 6), null,
                            "Fonctionnalités de bout en bout sur une application de réservation.")),
                    new Education("Diplôme d'ingénieur", "École Nationale Démo", "Informatique", 2022)),
            new Candidate("ilyas", "Ilyas", "Berrechid", "Casablanca", "Étudiant en développement web",
                    "Étudiant en dernière année, à la recherche d'un stage puis d'un premier poste en développement web.",
                    0, "B1", skills("html", "INTERMEDIATE", "css", "INTERMEDIATE", "javascript", "INTERMEDIATE",
                    "git", "BEGINNER"),
                    List.of(new Experience("Projet de fin d'études", "Université Démo", date(2025, 2), date(2025, 6),
                            "Application web de gestion de bibliothèque.")),
                    new Education("Licence", "Université Démo de Casablanca", "Développement web", 2025)));

    static final List<Job> JOBS = List.of(
            new Job("java", "atlasia", "Développeur·se Java / Spring Boot", "Développement logiciel", "Casablanca",
                    "HYBRID", "CDI", "MID", 3, 15000, 22000, true, "PUBLISHED", 20,
                    "Rejoignez l'équipe plateforme d'Atlasia Tech pour concevoir des API REST en Java et Spring Boot. "
                            + "Vous travaillerez sur la facturation et les paiements, avec PostgreSQL, Docker et une CI "
                            + "exigeante. Deux jours de télétravail par semaine.",
                    skills("java", "ADVANCED", "spring boot", "ADVANCED", "postgresql", "INTERMEDIATE"),
                    List.of("docker", "angular")),
            new Job("angular", "atlasia", "Développeur·se Angular", "Développement logiciel", "Casablanca", "HYBRID",
                    "CDI", "MID", 2, 13000, 18000, true, "PUBLISHED", 12,
                    "Nous recherchons un·e développeur·se Angular pour faire évoluer notre espace client : composants "
                            + "accessibles, signaux, tests unitaires et collaboration étroite avec le design.",
                    skills("angular", "ADVANCED", "typescript", "ADVANCED"), List.of("rest api", "tests unitaires")),
            new Job("devops", "atlasia", "Ingénieur·e DevOps", "DevOps et cloud", "Casablanca", "REMOTE", "CDI",
                    "SENIOR", 5, 22000, 30000, false, "PUBLISHED", 6,
                    "Vous industrialisez la plateforme : Kubernetes, pipelines CI/CD, observabilité et sécurité de la "
                            + "chaîne de livraison. Poste en télétravail complet avec deux séminaires par an.",
                    skills("docker", "ADVANCED", "kubernetes", "INTERMEDIATE", "ci/cd", "ADVANCED"),
                    List.of("terraform", "aws")),
            new Job("data", "atlasia", "Data analyst", "Data et intelligence artificielle", "Casablanca", "ON_SITE",
                    "CDD", "JUNIOR", 1, 9000, 12000, true, "DRAFT", 0,
                    "Brouillon : analyse des usages de la plateforme, tableaux de bord pour les équipes produit et "
                            + "commerciales, en lien avec l'équipe data.",
                    skills("sql", "INTERMEDIATE", "power bi", "INTERMEDIATE"), List.of("python")),
            new Job("stage", "atlasia", "Stage — développement web", "Développement logiciel", "Casablanca",
                    "ON_SITE", "INTERNSHIP", "JUNIOR", 0, null, null, false, "CLOSED", 60,
                    "Stage de fin d'études de six mois au sein de l'équipe web : participation aux sprints, revue de "
                            + "code et mise en production accompagnée.",
                    skills("html", "INTERMEDIATE", "javascript", "INTERMEDIATE"), List.of("git")),
            new Job("consultant", "novaterra", "Consultant·e fonctionnel·le", "Gestion de projet", "Rabat",
                    "HYBRID", "CDI", "MID", 3, 14000, 19000, true, "PUBLISHED", 15,
                    "Novaterra Conseil accompagne des entreprises dans leur transformation digitale. Vous animez les "
                            + "ateliers métier, rédigez les spécifications et conduisez le changement.",
                    skills("communication", "ADVANCED", "gestion de projet agile", "INTERMEDIATE"),
                    List.of("excel", "anglais professionnel")),
            new Job("chef-projet", "novaterra", "Chef·fe de projet digital", "Gestion de projet", "Rabat", "HYBRID",
                    "CDI", "SENIOR", 6, 20000, 26000, false, "PUBLISHED", 9,
                    "Vous pilotez des projets web et mobiles de bout en bout, en méthode Scrum, avec des équipes "
                            + "pluridisciplinaires et des clients exigeants.",
                    skills("scrum", "ADVANCED", "gestion de projet agile", "ADVANCED"), List.of("figma")),
            new Job("entrepot", "riad", "Responsable d'entrepôt", "Ingénierie et industrie", "Tanger", "ON_SITE",
                    "CDI", "SENIOR", 5, 16000, 21000, true, "PUBLISHED", 18,
                    "Riad Logistique recrute le ou la responsable de son entrepôt de Tanger Med : organisation des flux, "
                            + "encadrement de vingt personnes, sécurité et amélioration continue.",
                    skills("excel", "ADVANCED", "communication", "ADVANCED"), List.of("power bi")),
            new Job("supply", "riad", "Analyste supply chain", "Data et intelligence artificielle", "Tanger",
                    "ON_SITE", "CDI", "MID", 2, 12000, 15000, true, "ARCHIVED", 90,
                    "Offre archivée : prévisions de charge, optimisation des stocks et suivi des indicateurs "
                            + "logistiques de nos entrepôts.",
                    skills("excel", "ADVANCED", "sql", "INTERMEDIATE"), List.of("power bi")),
            new Job("designer", "saffran", "Designer UX/UI", "Design UX/UI", "Marrakech", "HYBRID", "CDI", "MID", 2,
                    12000, 16000, true, "CLOSED", 40,
                    "Offre clôturée lors de la désactivation de l'entreprise : conception d'interfaces, tests "
                            + "utilisateurs et design system.",
                    skills("figma", "ADVANCED"), List.of("html", "css")));
}
