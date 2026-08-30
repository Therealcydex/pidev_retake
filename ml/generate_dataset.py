"""
Generates the dataset used by the recommendation notebook.

The production database holds only a handful of rows, which is not enough to learn
anything from, so this script produces a realistic catalogue and enrolment history with
the SAME shape as the real tables (formations, categories, app_user, inscriptions).
Swapping the notebook's CSV loader for a SQL query later is then a one-line change.

The data is synthetic and this file is the proof of how it was made. Each user is given
a hidden interest profile — one or two preferred categories and a preferred level — and
their enrolments are drawn from it. Rediscovering that hidden structure is exactly the
job of the recommender, which also gives us a way to check that it works.

Run:  python ml/generate_dataset.py
"""

import csv
import random
from datetime import date, timedelta
from pathlib import Path

SEED = 42                    # fixed so the dataset is reproducible

# Les identifiants sont ceux de la BASE, et non des numéros locaux : le modèle indexe
# les apprenants par user_id, donc les CSV et la base doivent coïncider, sans quoi un
# apprenant connecté ne serait pas reconnu. Ces plages sont libres (formations
# s'arrête à 11, app_user à 19) : les données réelles sont préservées.
ID_DEPART_FORMATION = 101
ID_DEPART_USER = 1001
N_USERS = 200
N_FORMATIONS = 60
MIN_ENROLMENTS, MAX_ENROLMENTS = 2, 14

OUT = Path(__file__).parent / "data"

NIVEAUX = ["DEBUTANT", "INTERMEDIAIRE", "AVANCE"]

# The enum values are unaccented; the descriptions should read as proper French,
# and they feed the TF-IDF model later.
NIVEAU_LABEL = {
    "DEBUTANT": "débutant",
    "INTERMEDIAIRE": "intermédiaire",
    "AVANCE": "avancé",
}

# Category -> (topic words used to build titles, description fragment)
CATALOGUE = {
    "Développement Web": (
        ["HTML & CSS", "JavaScript moderne", "Angular", "React", "Vue.js", "Node.js",
         "Spring Boot", "API REST", "TypeScript", "Symfony"],
        "développement d'applications web côté client et serveur",
    ),
    "Data Science": (
        ["Python pour la data", "Pandas", "Visualisation de données", "Statistiques",
         "SQL analytique", "Machine Learning", "Séries temporelles"],
        "analyse de données, statistiques et modèles prédictifs",
    ),
    "DevOps": (
        ["Docker", "Kubernetes", "CI/CD avec Jenkins", "GitHub Actions", "Terraform",
         "Monitoring et logs", "Ansible"],
        "automatisation, conteneurisation et déploiement continu",
    ),
    "Cybersécurité": (
        ["Sécurité des applications", "Cryptographie", "Tests d'intrusion",
         "Sécurité réseau", "OWASP Top 10", "Gestion des identités"],
        "protection des systèmes, des données et des utilisateurs",
    ),
    "Cloud": (
        ["AWS", "Azure", "Architecture cloud", "Serverless", "Stockage cloud"],
        "conception et exploitation d'architectures cloud",
    ),
    "Mobile": (
        ["Android avec Kotlin", "iOS avec Swift", "Flutter", "React Native"],
        "développement d'applications mobiles natives et hybrides",
    ),
    "Management": (
        ["Gestion de projet agile", "Scrum", "Leadership d'équipe",
         "Communication professionnelle", "Gestion du temps"],
        "pilotage d'équipe, méthodes agiles et organisation",
    ),
    "Design UX/UI": (
        ["Principes UX", "Figma", "Design systems", "Accessibilité web",
         "Prototypage rapide"],
        "conception d'interfaces claires, utilisables et accessibles",
    ),
    "Réseaux": (
        ["Fondamentaux TCP/IP", "Administration Linux", "Routage et commutation",
         "VPN et pare-feu"],
        "infrastructure réseau, protocoles et administration système",
    ),
    "Intelligence Artificielle": (
        ["Introduction à l'IA", "Deep Learning", "Traitement du langage naturel",
         "Vision par ordinateur", "Systèmes de recommandation"],
        "modèles d'apprentissage automatique et applications intelligentes",
    ),
}


def build_formations(rng):
    """One row per formation, mirroring the `formations` table."""
    rows, fid = [], ID_DEPART_FORMATION
    topics = [(cat, t) for cat, (ts, _) in CATALOGUE.items() for t in ts]
    rng.shuffle(topics)

    for categorie, topic in topics[:N_FORMATIONS]:
        niveau = rng.choices(NIVEAUX, weights=[0.4, 0.4, 0.2])[0]
        theme = CATALOGUE[categorie][1]
        rows.append({
            "formation_id": fid,
            "titre": topic,
            "categorie": categorie,
            "niveau": niveau,
            "description": f"Formation {NIVEAU_LABEL[niveau]} en {topic} : {theme}.",
            "chapitres": rng.randint(4, 15),
        })
        fid += 1
    return rows


def build_users(rng, categories):
    """
    One row per trainee. `categorie_preferee` and `niveau_prefere` are the hidden
    profile the recommender is supposed to rediscover — they are written out only so
    the notebook can verify its clusters, never used as model input.
    """
    rows = []
    for uid in range(ID_DEPART_USER, ID_DEPART_USER + N_USERS):
        primary = rng.choice(categories)
        secondary = rng.choice([c for c in categories if c != primary])
        rows.append({
            "user_id": uid,
            "username": f"apprenant{uid - ID_DEPART_USER + 1:03d}",
            "categorie_preferee": primary,
            "categorie_secondaire": secondary,
            "niveau_prefere": rng.choices(NIVEAUX, weights=[0.45, 0.35, 0.20])[0],
        })
    return rows


def build_inscriptions(rng, users, formations):
    """
    Enrolments drawn from each user's hidden profile. Each *draw* picks a bucket with
    weights 70 / 20 / 10 (preferred category / secondary / anywhere); the 10% is
    deliberate noise, because a model that only works on perfectly consistent users
    proves nothing.

    The realised mix is flatter than 70/20/10 — around 50/30/20 — because a user who
    wants 14 distinct formations cannot get them all from a category holding 4, so the
    surplus spills into the other buckets. That is realistic, so it is left as is;
    main() prints the actual mix on every run rather than leaving this comment to be
    believed.
    """
    by_category = {}
    for f in formations:
        by_category.setdefault(f["categorie"], []).append(f)

    start = date(2025, 9, 1)
    rows = []

    for u in users:
        n = rng.randint(MIN_ENROLMENTS, MAX_ENROLMENTS)
        chosen = set()

        while len(chosen) < n:
            bucket = rng.choices(
                [u["categorie_preferee"], u["categorie_secondaire"], None],
                weights=[0.70, 0.20, 0.10],
            )[0]
            pool = by_category[bucket] if bucket else formations

            # Prefer the user's level, but not exclusively.
            preferred = [f for f in pool if f["niveau"] == u["niveau_prefere"]]
            f = rng.choice(preferred if preferred and rng.random() < 0.6 else pool)
            chosen.add(f["formation_id"])

        for fid in chosen:
            rows.append({
                "user_id": u["user_id"],
                "formation_id": fid,
                "date_inscription": (start + timedelta(days=rng.randint(0, 330))).isoformat(),
            })

    return rows


def write_csv(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"{path.name:24} {len(rows):5} rows")


def main():
    rng = random.Random(SEED)

    formations = build_formations(rng)
    users = build_users(rng, sorted(CATALOGUE.keys()))
    inscriptions = build_inscriptions(rng, users, formations)

    write_csv(OUT / "formations.csv", formations)
    write_csv(OUT / "users.csv", users)
    write_csv(OUT / "inscriptions.csv", inscriptions)

    print(f"\nMoyenne d'inscriptions par apprenant : "
          f"{len(inscriptions) / len(users):.1f}")

    # Report the mix that was actually produced, so the docstring above never has to be
    # taken on trust — this is the structure the recommender is meant to rediscover.
    cat_of = {f["formation_id"]: f["categorie"] for f in formations}
    profile = {u["user_id"]: u for u in users}

    primary = secondary = 0
    for i in inscriptions:
        u, categorie = profile[i["user_id"]], cat_of[i["formation_id"]]
        if categorie == u["categorie_preferee"]:
            primary += 1
        elif categorie == u["categorie_secondaire"]:
            secondary += 1

    total = len(inscriptions)
    print("Répartition réelle des inscriptions :")
    print(f"  catégorie préférée   : {primary / total:.0%}")
    print(f"  catégorie secondaire : {secondary / total:.0%}")
    print(f"  ailleurs (bruit)     : {(total - primary - secondary) / total:.0%}")


if __name__ == "__main__":
    main()
