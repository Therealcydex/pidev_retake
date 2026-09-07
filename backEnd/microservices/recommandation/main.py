"""
SkillUp — formation recommendation microservice.

    Angular :4200  ──►  Gateway :9090  ──►  Recommandation :8000  ──►  MySQL

Two speeds, on purpose: the population structure (similarities, K-Means) is built once
at startup; the caller's own history is re-read from MySQL on every request.

The study and its evaluation are in ml/recommandation_crisp_dm.ipynb.

    python main.py            →  http://localhost:8000/docs
"""

import os

# Must precede scikit-learn: silences a K-Means MKL warning on Windows.
os.environ.setdefault("OMP_NUM_THREADS", "1")

import numpy as np
import pandas as pd
import uvicorn
from fastapi import APIRouter, FastAPI, HTTPException, Query
from pydantic import BaseModel, Field
from sklearn.cluster import KMeans
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from sqlalchemy import create_engine, text

# --------------------------------------------------------------------------- config

BASE = os.environ.get(
    "SKILLUP_DB_URL",
    "mysql+pymysql://root@localhost:3306/pidev_formation?charset=utf8mb4",
)

EUREKA = os.environ.get("EUREKA_URL", "http://localhost:8761/eureka")
NOM_SERVICE = "recommandation"
PORT = int(os.environ.get("PORT", "8000"))

# In a container "localhost" means the container itself, so the gateway would resolve
# RECOMMANDATION to its own address. Docker sets this to the container name.
HOTE_PUBLIE = os.environ.get("EUREKA_INSTANCE_HOST", "localhost")

ALEA = 42               # fixed seed: same groups as the notebook, reproducible demo
N_VOISINS = 20          # nearest learners consulted by the collaborative part
POIDS_CONTENU = 0.5     # content / collaborative weighting in the hybrid

# pool_pre_ping: MySQL drops idle connections; without it the first request after a
# pause fails on a dead one.
moteur = create_engine(BASE, pool_pre_ping=True, future=True)

# --------------------------------------------------------------------------- requêtes

# description_detaillee, not description: the latter is the short card summary, and
# TF-IDF over truncated text would compare sentence openings.
SQL_FORMATIONS = text("""
    SELECT f.id                              AS formation_id,
           f.titre                           AS titre,
           COALESCE(c.nom, 'Sans catégorie') AS categorie,
           COALESCE(f.niveau, 'DEBUTANT')    AS niveau,
           COALESCE(NULLIF(f.description_detaillee, ''), f.description
           , '') AS description
    FROM formations f
    LEFT JOIN categories c ON c.id = f.categorie_id
""")

SQL_INSCRIPTIONS = text("""
    SELECT i.user_id, i.formation_id
    FROM inscriptions i
    JOIN formations f ON f.id = i.formation_id
    WHERE i.user_id IS NOT NULL
""")

SQL_MES_INSCRIPTIONS = text(
    "SELECT formation_id FROM inscriptions WHERE user_id = :uid")


def inscriptions_de(user_id: int) -> list[int]:
    """The caller's history, read at request time — this is the 'fast' speed."""
    with moteur.connect() as cx:
        return [int(r[0]) for r in
                cx.execute(SQL_MES_INSCRIPTIONS, {"uid": user_id}).fetchall()]


# --------------------------------------------------------------------------- modèle

class Modele:
    """Everything expensive: the same three representations as the notebook."""

    def __init__(self):
        formations = pd.read_sql(SQL_FORMATIONS, moteur)
        formations["description"] = formations["description"].fillna("")
        inscriptions = (pd.read_sql(SQL_INSCRIPTIONS, moteur)
                        .drop_duplicates(subset=["user_id", "formation_id"]))

        if formations.empty:
            raise RuntimeError("Aucune formation en base : rien à recommander.")

        self.formations = formations.set_index("formation_id")

        # TF-IDF weights words: "formation" appears everywhere and counts for almost
        # nothing, "Kubernetes" is rare and counts a lot.
        texte = (formations["titre"] + " " + formations["categorie"] + " "
                 + formations["niveau"] + " " + formations["description"])
        V = TfidfVectorizer(lowercase=True, strip_accents="unicode", min_df=1) \
            .fit_transform(texte)
        # Cosine compares the angle, not the length: a long description is not judged
        # less similar to a short one just because it has more words.
        self.sim_formations = pd.DataFrame(
            cosine_similarity(V),
            index=formations["formation_id"], columns=formations["formation_id"])

        # Binary matrix: enrolment is an implicit signal, there is no rating to model.
        self.matrice = (inscriptions.assign(v=1)
                        .pivot_table(index="user_id", columns="formation_id",
                                     values="v", fill_value=0)
                        .reindex(columns=formations["formation_id"], fill_value=0))

        self.sim_apprenants = pd.DataFrame(
            cosine_similarity(self.matrice.values),
            index=self.matrice.index, columns=self.matrice.index)
        # Everyone is perfectly similar to themselves — without this we would recommend
        # what the learner already follows.
        np.fill_diagonal(self.sim_apprenants.values, 0.0)

        # Profile = share of enrolments per category, then per level. Already
        # proportions in [0,1], so deliberately not standardised: scaling would give a
        # rare category the same weight as a very popular one.
        details = inscriptions.merge(
            formations[["formation_id", "categorie", "niveau"]], on="formation_id")
        self.profils = (
            pd.crosstab(details["user_id"], details["categorie"], normalize="index")
              .add_prefix("cat_")
              .join(pd.crosstab(details["user_id"], details["niveau"],
                                normalize="index").add_prefix("niv_"))
              .fillna(0.0))
        self.colonnes_profil = list(self.profils.columns)

        self.kmeans = self._segmenter(self.profils.values)

    @staticmethod
    def _segmenter(X: np.ndarray) -> KMeans | None:
        """K-Means for k = 2..15, keeping the best silhouette score.

        The point to defend: k is derived from the data, not picked by hand. k_max is
        bounded by the population — you cannot form more groups than individuals.
        """
        from sklearn.metrics import silhouette_score

        k_max = min(15, len(X) - 1)
        if k_max < 2:
            return None

        meilleur, score_max = None, -1.0
        for k in range(2, k_max + 1):
            km = KMeans(n_clusters=k, random_state=ALEA, n_init=10).fit(X)
            s = silhouette_score(X, km.labels_)
            if s > score_max:
                meilleur, score_max = km, s
        return meilleur

    # ------------------------------------------------------------------ utilitaires

    def profil_de(self, vues: list[int]) -> pd.Series | None:
        """Built on the fly rather than read from self.profils: a learner who enrolled
        since startup is absent from it, yet his profile is perfectly computable."""
        connues = [f for f in vues if f in self.formations.index]
        if not connues:
            return None

        lignes = self.formations.loc[connues]
        parts = pd.concat([
            lignes["categorie"].value_counts(normalize=True).add_prefix("cat_"),
            lignes["niveau"].value_counts(normalize=True).add_prefix("niv_"),
        ])
        return parts.reindex(self.colonnes_profil, fill_value=0.0)

    def groupe_de(self, vues: list[int]) -> int | None:
        profil = self.profil_de(vues)
        if profil is None or self.kmeans is None:
            return None
        return int(self.kmeans.predict([profil.values])[0])


modele = Modele()


def normaliser(s: pd.Series) -> pd.Series:
    """Both scores must reach [0,1] before being mixed — they are on different scales."""
    etendue = s.max() - s.min()
    return (s - s.min()) / etendue if etendue > 0 else s * 0.0


# --------------------------------------------------------------------------- schémas

class FormationSuggeree(BaseModel):
    formation_id: int
    titre: str
    categorie: str
    niveau: str
    score: float = Field(..., description="Pertinence, entre 0 et 1")


class Suggestions(BaseModel):
    user_id: int
    methode: str = Field(..., description="hybride, contenu ou populaire")
    groupe: int | None = Field(None, description="Segment K-Means de l'apprenant")
    deja_suivies: int
    suggestions: list[FormationSuggeree]


class ProfilApprenant(BaseModel):
    user_id: int
    groupe: int | None
    inscriptions: int
    categorie_dominante: str
    repartition_categories: dict[str, float]
    repartition_niveaux: dict[str, float]


# --------------------------------------------------------------------------- logique

def scores_contenu(vues: list[int]) -> pd.Series:
    """Content-based: resemblance to what the learner already follows."""
    return normaliser(modele.sim_formations.loc[vues].mean(axis=0))


def scores_collaboratif(user_id: int) -> pd.Series | None:
    """Collaborative: what the nearest learners follow.

    None when the learner is absent from the matrix — enrolled since the last build.
    """
    if user_id not in modele.sim_apprenants.index:
        return None
    voisins = modele.sim_apprenants.loc[user_id].nlargest(N_VOISINS)
    return normaliser(
        modele.matrice.loc[voisins.index].mul(voisins.values, axis=0).sum())


def populaires(k: int) -> pd.Series:
    """Cold-start fallback: with no history there is no signal to analyse."""
    compte = modele.matrice.sum().sort_values(ascending=False).head(k)
    return compte / compte.max() if compte.max() > 0 else compte


def en_reponse(scores: pd.Series) -> list[FormationSuggeree]:
    sortie = []
    for fid, score in scores.items():
        f = modele.formations.loc[fid]
        sortie.append(FormationSuggeree(
            formation_id=int(fid),
            titre=str(f["titre"]),
            categorie=str(f["categorie"]),
            niveau=str(f["niveau"]),
            score=round(float(score), 4),
        ))
    return sortie


# --------------------------------------------------------------------------- routes

app = FastAPI(
    title="SkillUp — Recommandation de formations",
    description="Analyse le profil d'un apprenant et suggère les formations pertinentes.",
    version="2.0.0",
)

# No CORS here on purpose: the browser never calls this service directly, it goes
# through the gateway, which already sets Access-Control-Allow-Origin. A duplicated
# CORS header is as invalid as a missing one.

router = APIRouter(prefix="/recommandations", tags=["recommandation"])


@router.get("/sante", summary="État du service")
def sante():
    return {
        "statut": "ok",
        "apprenants": int(modele.matrice.shape[0]),
        "formations": int(modele.matrice.shape[1]),
        "groupes": int(modele.kmeans.n_clusters) if modele.kmeans else None,
    }


@router.post("/recharger", summary="Recalculer le modèle depuis la base")
def recharger():
    """Rebuilds the slow part after new formations are added, without a restart.

    Enrolments need no reload — they are re-read on every request.
    """
    global modele
    modele = Modele()
    return sante()


@router.get("/profil/{user_id}", response_model=ProfilApprenant,
            summary="Profil analysé d'un apprenant")
def profil(user_id: int):
    """What makes a suggestion explainable: « vous êtes surtout sur DevOps »."""
    vues = inscriptions_de(user_id)
    p = modele.profil_de(vues)
    if p is None:
        raise HTTPException(404, f"Aucune inscription pour l'apprenant {user_id}")

    cats = {c.replace("cat_", ""): round(float(v), 4)
            for c, v in p.filter(like="cat_").items() if v > 0}
    nivs = {n.replace("niv_", ""): round(float(v), 4)
            for n, v in p.filter(like="niv_").items() if v > 0}

    return ProfilApprenant(
        user_id=user_id,
        groupe=modele.groupe_de(vues),
        inscriptions=len(vues),
        categorie_dominante=max(cats, key=cats.get),
        repartition_categories=dict(sorted(cats.items(), key=lambda kv: -kv[1])),
        repartition_niveaux=nivs,
    )


# Declared last: literal paths must come before /{user_id} or they never match.
@router.get("/{user_id}", response_model=Suggestions,
            summary="Formations suggérées pour un apprenant")
def suggestions(user_id: int, k: int = Query(5, ge=1, le=20)):
    """Three cases, and the response always says which one applied:

      hybride    — content + collaborative, the normal case
      contenu    — learner absent from the last build: no known neighbours, but his
                   own tastes are still readable
      populaire  — no enrolment at all: nothing to analyse, offer the most followed

    A generic suggestion is never presented as if it were personalised.
    """
    vues = [f for f in inscriptions_de(user_id) if f in modele.sim_formations.index]

    if not vues:
        return Suggestions(user_id=user_id, methode="populaire", groupe=None,
                           deja_suivies=0, suggestions=en_reponse(populaires(k)))

    s_contenu = scores_contenu(vues)
    s_collab = scores_collaboratif(user_id)

    if s_collab is None:
        methode, total = "contenu", s_contenu
    else:
        methode = "hybride"
        total = POIDS_CONTENU * s_contenu + (1 - POIDS_CONTENU) * s_collab

    return Suggestions(
        user_id=user_id,
        methode=methode,
        groupe=modele.groupe_de(vues),
        deja_suivies=len(vues),
        # drop(index=vues): never recommend what the learner already follows.
        suggestions=en_reponse(total.drop(index=vues).nlargest(k)),
    )


app.include_router(router)


# --------------------------------------------------------------------------- Eureka

@app.on_event("startup")
async def enregistrer():
    """Registers with Eureka like the Spring services, so the gateway resolves this
    service by name (lb://RECOMMANDATION) instead of a hard-coded address.

    Failure is not fatal: startup is not blocked for service discovery.
    """
    try:
        from py_eureka_client import eureka_client
        await eureka_client.init_async(
            eureka_server=EUREKA,
            app_name=NOM_SERVICE,
            instance_port=PORT,
            instance_host=HOTE_PUBLIE,
        )
        print(f"Enregistre aupres d'Eureka : {EUREKA} (publie comme {HOTE_PUBLIE}:{PORT})")
    except Exception as e:
        print(f"Eureka indisponible ({e}) — le service fonctionne quand meme.")


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=PORT)
