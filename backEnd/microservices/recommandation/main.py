"""

Besoin MLA : « analyser des profils pour suggérer les formations les plus pertinentes ».

L'analyse et son évaluation (PCA, choix de k, précision@k) sont dans ml/pidev_retake.ipynb.
Ce service applique le même modèle, mais sur les données réelles :

    Angular :4200  ──►  Gateway :9090  ──►  Recommandation :8000  ──►  MySQL

Deux vitesses, volontairement séparées :

  * la structure de la population — similarité entre formations, similarité entre
    apprenants, segments K-Means — est calculée une fois au démarrage. Elle ne change
    que si le catalogue ou la population changent ;

  * l'historique de l'apprenant qui appelle est relu dans MySQL à chaque requête. C'est
    c


Démarrage :
    python main.py
Documentation interactive :
    http://localhost:8000/docs
"""

import os

# OMP_NUM_THREADS avant scikit-learn : sous Windows, K-Means émet sinon un
# avertissement MKL à chaque appel.
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

# Le schéma du microservice Formation : on lit exactement ce que l'application écrit.
BASE = os.environ.get(
    "SKILLUP_DB_URL",
    "mysql+pymysql://root@localhost:3306/pidev_formation?charset=utf8mb4",
)

EUREKA = os.environ.get("EUREKA_URL", "http://localhost:8761/eureka")
NOM_SERVICE = "recommandation"
PORT = int(os.environ.get("PORT", "8000"))

# L'adresse que ce service publie dans Eureka — celle que la gateway utilisera pour
# l'appeler. Hors conteneur, « localhost » convient : tout tourne sur la meme machine.
# Dans un conteneur, non : « localhost » y designe le conteneur lui-meme, et la gateway
# resolverait RECOMMANDATION vers sa propre adresse. Il faut alors publier un nom
# joignable depuis la gateway (le nom du conteneur, ou l'adresse de l'hote).
HOTE_PUBLIE = os.environ.get("EUREKA_INSTANCE_HOST", "localhost")

ALEA = 42               # même graine que le notebook, donc mêmes groupes
N_VOISINS = 20          # apprenants proches consultés par le collaboratif
POIDS_CONTENU = 0.5     # pondération contenu / collaboratif dans l'hybride

# pool_pre_ping : MySQL ferme les connexions inactives ; sans ce test la première
# requête après une pause échouerait sur une connexion morte.
moteur = create_engine(BASE, pool_pre_ping=True, future=True)

# --------------------------------------------------------------------------- requêtes

# description_detaillee et non description : cette dernière est un VARCHAR(50), le
# résumé affiché sur la carte. Un TF-IDF sur des textes tronqués à 50 caractères
# comparerait des débuts de phrase.
SQL_FORMATIONS = text("""
    SELECT f.id                              AS formation_id,
           f.titre                           AS titre,
           COALESCE(c.nom, 'Sans catégorie') AS categorie,
           COALESCE(f.niveau, 'DEBUTANT')    AS niveau,
           COALESCE(NULLIF(f.description_detaillee, ''), f.description, '') AS description
    FROM formations f
    LEFT JOIN categories c ON c.id = f.categorie_id
""")

SQL_INSCRIPTIONS = text("""
    SELECT i.user_id, i.formation_id
    FROM inscriptions i
    JOIN formations f ON f.id = i.formation_id
    WHERE i.user_id IS NOT NULL
""")

# La requête du chemin chaud : un seul apprenant, donc un accès index.
SQL_MES_INSCRIPTIONS = text(
    "SELECT formation_id FROM inscriptions WHERE user_id = :uid")


def inscriptions_de(user_id: int) -> list[int]:
    """L'historique de l'apprenant, lu à l'instant de la requête."""
    with moteur.connect() as cx:
        return [int(r[0]) for r in
                cx.execute(SQL_MES_INSCRIPTIONS, {"uid": user_id}).fetchall()]


# --------------------------------------------------------------------------- modèle

class Modele:
    """
    La partie coûteuse, reconstruite au démarrage et par POST /recharger.

    Reprend pas à pas le notebook : matrice binaire, profils par proportions,
    K-Means sur ces profils, TF-IDF sur le texte des formations.
    """

    def __init__(self):
        formations = pd.read_sql(SQL_FORMATIONS, moteur)
        formations["description"] = formations["description"].fillna("")
        inscriptions = (pd.read_sql(SQL_INSCRIPTIONS, moteur)
                        .drop_duplicates(subset=["user_id", "formation_id"]))

        if formations.empty:
            raise RuntimeError("Aucune formation en base : rien à recommander.")

        self.formations = formations.set_index("formation_id")

        # --- contenu : similarité formation x formation ---------------------------
        texte = (formations["titre"] + " " + formations["categorie"] + " "
                 + formations["niveau"] + " " + formations["description"])
        V = TfidfVectorizer(lowercase=True, strip_accents="unicode", min_df=1) \
            .fit_transform(texte)
        self.sim_formations = pd.DataFrame(
            cosine_similarity(V),
            index=formations["formation_id"], columns=formations["formation_id"])

        # --- matrice binaire apprenant x formation --------------------------------
        self.matrice = (inscriptions.assign(v=1)
                        .pivot_table(index="user_id", columns="formation_id",
                                     values="v", fill_value=0)
                        .reindex(columns=formations["formation_id"], fill_value=0))

        # --- collaboratif : similarité apprenant x apprenant ----------------------
        self.sim_apprenants = pd.DataFrame(
            cosine_similarity(self.matrice.values),
            index=self.matrice.index, columns=self.matrice.index)
        # Un apprenant ne se recommande pas lui-même.
        np.fill_diagonal(self.sim_apprenants.values, 0.0)

        # --- profils : part des inscriptions par catégorie, puis par niveau -------
        details = inscriptions.merge(
            formations[["formation_id", "categorie", "niveau"]], on="formation_id")
        self.profils = (
            pd.crosstab(details["user_id"], details["categorie"], normalize="index")
              .add_prefix("cat_")
              .join(pd.crosstab(details["user_id"], details["niveau"],
                                normalize="index").add_prefix("niv_"))
              .fillna(0.0))
        self.colonnes_profil = list(self.profils.columns)

        # --- segmentation ---------------------------------------------------------
        # k choisi par silhouette, comme dans le notebook. Le maximum est borné par le
        # nombre d'apprenants : on ne peut pas former plus de groupes que d'individus.
        self.kmeans = self._segmenter(self.profils.values)

    @staticmethod
    def _segmenter(X: np.ndarray) -> KMeans | None:
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
        """
        Construit le vecteur de profil d'un apprenant à partir de son historique réel.

        Calculé à la volée plutôt que lu dans `self.profils` : un apprenant inscrit
        depuis le démarrage du service n'y figure pas encore, et il a pourtant un
        profil parfaitement calculable.
        """
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
    """Ramène une série entre 0 et 1 : les deux scores n'ont pas la même échelle."""
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
    """Ressemblance avec ce que l'apprenant a déjà suivi."""
    return normaliser(modele.sim_formations.loc[vues].mean(axis=0))


def scores_collaboratif(user_id: int) -> pd.Series | None:
    """Ce que les apprenants les plus proches ont suivi. None si l'apprenant est
    absent de la matrice — inscrit depuis le dernier calcul, par exemple."""
    if user_id not in modele.sim_apprenants.index:
        return None
    voisins = modele.sim_apprenants.loc[user_id].nlargest(N_VOISINS)
    return normaliser(
        modele.matrice.loc[voisins.index].mul(voisins.values, axis=0).sum())


def populaires(k: int) -> pd.Series:
    """Repli du démarrage à froid : les formations les plus suivies."""
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
# L'ordre compte : les chemins littéraux sont déclarés avant /{user_id}.

app = FastAPI(
    title="SkillUp — Recommandation de formations",
    description="Analyse le profil d'un apprenant et suggère les formations pertinentes.",
    version="2.0.0",
)

# Pas de CORS ici, volontairement : le navigateur ne parle jamais à ce service
# directement, il passe par la gateway, qui ajoute déjà Access-Control-Allow-Origin.
# Un en-tête CORS en double est aussi invalide qu'un en-tête absent.

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
    """
    À appeler après un ajout de formations. L'historique des apprenants, lui, est déjà
    relu à chaque requête : il n'a pas besoin de ce rechargement.
    """
    global modele
    modele = Modele()
    return sante()


@router.get("/similaires/{formation_id}", response_model=list[FormationSuggeree],
            summary="Formations proches d'une formation")
def similaires(formation_id: int, k: int = Query(5, ge=1, le=20)):
    """
    Similarité de contenu seule : aucune inscription n'intervient. C'est ce qui permet
    de recommander une formation que personne n'a encore suivie.
    """
    if formation_id not in modele.sim_formations.index:
        raise HTTPException(404, f"Formation {formation_id} inconnue")
    scores = modele.sim_formations.loc[formation_id].drop(index=formation_id).nlargest(k)
    return en_reponse(scores)


@router.get("/profil/{user_id}", response_model=ProfilApprenant,
            summary="Profil analysé d'un apprenant")
def profil(user_id: int):
    """Le segment de l'apprenant et la répartition de ses inscriptions — c'est ce qui
    rend une suggestion explicable : « vous êtes surtout sur DevOps »."""
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


@router.get("/{user_id}", response_model=Suggestions,
            summary="Formations suggérées pour un apprenant")
def suggestions(user_id: int, k: int = Query(5, ge=1, le=20)):
    """
    Les `k` formations les plus pertinentes, d'après l'historique **actuel** de
    l'apprenant, relu en base à chaque appel.

    Trois cas, et la réponse dit toujours lequel s'est appliqué :
      * hybride  — contenu + collaboratif, le cas normal ;
      * contenu  — apprenant absent du dernier calcul (inscrit depuis le démarrage) :
                   ses voisins sont inconnus, mais ses goûts, eux, sont lisibles ;
      * populaire — aucune inscription : rien à analyser, on propose les plus suivies.
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
        suggestions=en_reponse(total.drop(index=vues).nlargest(k)),
    )


app.include_router(router)


# --------------------------------------------------------------------------- Eureka

@app.on_event("startup")
async def enregistrer():
    """S'inscrire auprès d'Eureka, comme les microservices Spring.

    L'échec n'est pas fatal : le service reste joignable par son adresse directe, la
    gateway sait la trouver. On ne bloque pas le démarrage pour de la découverte.
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
