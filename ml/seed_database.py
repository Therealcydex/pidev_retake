"""
Génère le script SQL qui charge le jeu de données dans la base réelle.

Pourquoi : le modèle de recommandation identifie les apprenants par leur `user_id`. Tant
que les identifiants du jeu de données ne correspondent pas à ceux de `app_user`, un
apprenant connecté ne peut pas recevoir de recommandations. Ce script insère les données
générées dans les vraies tables, avec des identifiants fixes, pour que les deux mondes
coïncident.

Le script SQL est écrit sur disque plutôt qu'exécuté directement : il reste dans le dépôt,
lisible, et montre exactement ce qui a été inséré.

    python ml/seed_database.py
    mysql -u root pidev_formation < ml/seed_database.sql

Choix des identifiants — les données réelles ne sont jamais touchées :
    formations   101 .. 158     (la base s'arrête à 11)
    apprenants  1001 .. 1200    (app_user s'arrête à 19)
Relancer le script est sans risque : chaque bloc supprime d'abord sa propre plage.
"""

import csv
from pathlib import Path

ICI = Path(__file__).parent
DATA = ICI / "data"
SQL = ICI / "seed_database.sql"


# Toutes les comptes générés partagent le mot de passe « Test1234! ».
# Le hachage est repris d'un compte existant, pour ne pas dépendre de bcrypt en Python.
HASH_MDP = "$2a$10$eX1lNJh.FShM71DhOlZZQ.OkkEg7IK10Wq.6V2xMKn5/HxG3MUajy"

# `formations.description` est un VARCHAR(50) : le texte long va dans
# `description_detaillee`, exactement comme le fait l'application.
LIMITE_DESCRIPTION = 50


def lire(nom):
    with (DATA / nom).open(encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def echapper(valeur):
    return "'" + str(valeur).replace("\\", "\\\\").replace("'", "''") + "'"


def resumer(texte, limite=LIMITE_DESCRIPTION):
    """Coupe proprement sur un mot, sans dépasser la limite de la colonne."""
    if len(texte) <= limite:
        return texte
    coupe = texte[:limite - 1].rsplit(" ", 1)[0]
    return (coupe + "…")[:limite]


def main():
    formations = lire("formations.csv")
    users = lire("users.csv")
    inscriptions = lire("inscriptions.csv")

    categories = sorted({f["categorie"] for f in formations})
    # Les CSV portent déjà les identifiants de la base (voir generate_dataset.py).
    id_formation = {f["formation_id"]: int(f["formation_id"]) for f in formations}
    id_user = {u["user_id"]: int(u["user_id"]) for u in users}
    SEUIL_FORMATION = min(id_formation.values()) - 1
    SEUIL_USER = min(id_user.values()) - 1

    l = []
    a = l.append

    a("-- Généré par ml/seed_database.py — ne pas modifier à la main.")
    a("-- Charge le jeu de données de recommandation dans les tables réelles.")
    a("")
    a("SET NAMES utf8mb4;")
    a("")

    # ---------------------------------------------------------------- catégories
    a("-- 1. Catégories -----------------------------------------------------------")
    a("-- `nom` est UNIQUE : INSERT IGNORE laisse en place celles qui existent déjà.")
    for nom in categories:
        a(f"INSERT IGNORE INTO categories (nom) VALUES ({echapper(nom)});")
    a("")

    # ---------------------------------------------------------------- formations
    a("-- 2. Formations -----------------------------------------------------------")
    a(f"-- Plage {SEUIL_FORMATION + 1}..{SEUIL_FORMATION + len(formations)} ; "
      "les formations existantes ne sont pas touchées.")
    a("DELETE FROM inscriptions WHERE formation_id > %d;" % SEUIL_FORMATION)
    a("DELETE FROM formations   WHERE id > %d;" % SEUIL_FORMATION)
    a("")
    for f in formations:
        a("INSERT INTO formations (id, titre, description, description_detaillee, "
          "niveau, categorie_id, owner_id) VALUES ("
          f"{id_formation[f['formation_id']]}, "
          f"{echapper(f['titre'])}, "
          f"{echapper(resumer(f['description']))}, "
          f"{echapper(f['description'])}, "
          f"{echapper(f['niveau'])}, "
          f"(SELECT id FROM categories WHERE nom = {echapper(f['categorie'])}), "
          "NULL);")
    a("")

    # ---------------------------------------------------------------- apprenants
    a("-- 3. Apprenants -----------------------------------------------------------")
    a("-- Dans l'autre base : à exécuter sur pidev_user.")
    a(f"-- Plage {SEUIL_USER + 1}..{SEUIL_USER + len(users)}, rôle TRAINEE, "
      "mot de passe « Test1234! ».")
    a("")

    # ---------------------------------------------------------------- inscriptions
    a("-- 4. Inscriptions ---------------------------------------------------------")
    for i in inscriptions:
        a("INSERT INTO inscriptions (user_id, formation_id, date_inscription) VALUES ("
          f"{id_user[i['user_id']]}, "
          f"{id_formation[i['formation_id']]}, "
          f"{echapper(i['date_inscription'] + ' 12:00:00')});")
    a("")
    a("SELECT COUNT(*) AS formations_chargees FROM formations WHERE id > %d;"
      % SEUIL_FORMATION)
    a("SELECT COUNT(*) AS inscriptions_chargees FROM inscriptions WHERE user_id > %d;"
      % SEUIL_USER)

    SQL.write_text("\n".join(l) + "\n", encoding="utf-8")

    # Le fichier des apprenants est séparé : il vise l'autre base de données.
    u = ["-- Généré par ml/seed_database.py — à exécuter sur pidev_user.",
         "SET NAMES utf8mb4;",
         "",
         f"DELETE FROM app_user WHERE id > {SEUIL_USER};",
         ""]
    for user in users:
        u.append("INSERT INTO app_user (id, username, email, password, role) VALUES ("
                 f"{id_user[user['user_id']]}, "
                 f"{echapper(user['username'])}, "
                 f"{echapper(user['username'] + '@skillup.tn')}, "
                 f"{echapper(HASH_MDP)}, 'TRAINEE');")
    u.append("")
    u.append(f"SELECT COUNT(*) AS apprenants_charges FROM app_user WHERE id > {SEUIL_USER};")

    (ICI / "seed_users.sql").write_text("\n".join(u) + "\n", encoding="utf-8")

    print(f"{SQL.name:22} {len(formations)} formations, {len(inscriptions)} inscriptions")
    print(f"{'seed_users.sql':22} {len(users)} apprenants")
    print(f"\nIdentifiants : formations {SEUIL_FORMATION + 1}.."
          f"{SEUIL_FORMATION + len(formations)}, "
          f"apprenants {SEUIL_USER + 1}..{SEUIL_USER + len(users)}")


if __name__ == "__main__":
    main()
