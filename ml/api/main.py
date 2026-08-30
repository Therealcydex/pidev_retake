"""
API de recommandation de formations — SkillUp.

Sert le modèle construit dans ml/pidev_retake.ipynb. Aucun apprentissage ici : les
similarités sont déjà calculées et chargées depuis le fichier joblib au démarrage, donc
une requête n'est qu'une lecture suivie d'un tri.

Toutes les routes sont sous /recommandations, ce qui permet à la gateway de n'en déclarer
qu'une seule :

    Angular :4200  ──►  Gateway :9090  ──►  FastAPI :8000

Démarrage :
    python -m uvicorn ml.api.main:app --port 8000 --reload
Documentation interactive :
    http://localhost:8000/docs
"""

from pathlib import Path

import joblib
import pandas as pd
from fastapi import APIRouter, FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

MODELE = Path(__file__).resolve().parent.parent / "modele" / "recommandation.joblib"

N_VOISINS = 20          # nombre d'apprenants proches consultés par le collaboratif

app = FastAPI(
    title="SkillUp — Recommandation de formations",
    description="Analyse le profil d'un apprenant et suggère les formations pertinentes.",
    version="1.0.0",
)

# Pas de CORS ici, volontairement.
#
# Le navigateur ne parle jamais à ce service directement : il appelle la gateway, qui
# ajoute déjà l'en-tête Access-Control-Allow-Origin. Si les deux l'ajoutaient, le
# navigateur verrait « http://localhost:4200, http://localhost:4200 » et refuserait la
# réponse — un en-tête CORS en double est aussi invalide qu'un en-tête absent.
#
# La documentation /docs continue de fonctionner : elle est servie par ce même service,
# donc les appels y sont de même origine et ne relèvent pas du CORS.

router = APIRouter(prefix="/recommandations", tags=["recommandation"])


# --------------------------------------------------------------------------- modèle

class Modele:
    """Le fichier joblib, chargé une seule fois au démarrage."""

    def __init__(self, chemin: Path):
        if not chemin.exists():
            raise FileNotFoundError(
                f"Modèle introuvable : {chemin}\n"
                "Exécuter ml/pidev_retake.ipynb pour le générer."
            )
        a = joblib.load(chemin)
        self.sim_formations: pd.DataFrame = a["sim_formations"]
        self.matrice: pd.DataFrame = a["matrice"]
        self.sim_apprenants: pd.DataFrame = a["sim_apprenants"]
        self.formations: pd.DataFrame = a["formations"]
        self.profils: pd.DataFrame = a["profils"]
        self.kmeans = a["kmeans"]
        self.poids_contenu: float = a.get("poids_contenu", 0.5)

    def suivies(self, user_id: int) -> list[int]:
        """Formations déjà suivies — jamais recommandées à nouveau."""
        if user_id not in self.matrice.index:
            return []
        ligne = self.matrice.loc[user_id]
        return ligne[ligne > 0].index.tolist()

    def groupe(self, user_id: int) -> int | None:
        if user_id not in self.profils.index:
            return None
        return int(self.kmeans.predict(self.profils.loc[[user_id]].values)[0])


modele = Modele(MODELE)


def _normaliser(s: pd.Series) -> pd.Series:
    """Ramène une série entre 0 et 1 pour pouvoir additionner les deux scores."""
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
    methode: str = Field(..., description="hybride ou populaire")
    groupe: int | None = Field(None, description="Segment K-Means de l'apprenant")
    deja_suivies: int
    suggestions: list[FormationSuggeree]


class ProfilApprenant(BaseModel):
    user_id: int
    groupe: int
    inscriptions: int
    categorie_dominante: str
    repartition_categories: dict[str, float]
    repartition_niveaux: dict[str, float]


# --------------------------------------------------------------------------- logique

def _scores_hybrides(user_id: int) -> pd.Series:
    """
    Les deux signaux du notebook, combinés :
      - contenu      : ressemblance avec ce que l'apprenant a déjà suivi ;
      - collaboratif : ce que les apprenants les plus proches ont suivi.
    Chacun est ramené entre 0 et 1 avant l'addition, car ils n'ont pas la même échelle.
    """
    vues = modele.suivies(user_id)

    s_contenu = _normaliser(modele.sim_formations.loc[vues].mean(axis=0))

    voisins = modele.sim_apprenants.loc[user_id].nlargest(N_VOISINS)
    s_collab = _normaliser(
        modele.matrice.loc[voisins.index].mul(voisins.values, axis=0).sum()
    )

    p = modele.poids_contenu
    return (p * s_contenu + (1 - p) * s_collab).drop(index=vues)


def _populaires(k: int) -> pd.Series:
    """Repli du démarrage à froid : les formations les plus suivies."""
    compte = modele.matrice.sum().sort_values(ascending=False).head(k)
    return compte / compte.max() if compte.max() > 0 else compte


def _reponse(scores: pd.Series) -> list[FormationSuggeree]:
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

@router.get("/sante", summary="État du service")
def sante():
    return {
        "statut": "ok",
        "apprenants": int(modele.matrice.shape[0]),
        "formations": int(modele.matrice.shape[1]),
        "groupes": int(modele.kmeans.n_clusters),
    }


@router.get("/similaires/{formation_id}", response_model=list[FormationSuggeree],
            summary="Formations proches d'une formation")
def similaires(formation_id: int, k: int = Query(5, ge=1, le=20)):
    """
    Similarité de contenu uniquement : aucune inscription n'est utilisée.

    C'est ce qui permet de recommander une formation que personne n'a encore suivie —
    le collaboratif, lui, ne saurait rien en dire.
    """
    if formation_id not in modele.sim_formations.index:
        raise HTTPException(404, f"Formation {formation_id} inconnue du modèle")

    scores = modele.sim_formations.loc[formation_id].drop(index=formation_id).nlargest(k)
    return _reponse(scores)


@router.get("/profil/{user_id}", response_model=ProfilApprenant,
            summary="Profil analysé d'un apprenant")
def profil(user_id: int):
    """
    Le segment de l'apprenant et la répartition de ses inscriptions.
    C'est ce qui rend une suggestion explicable : « vous êtes surtout sur DevOps ».
    """
    if user_id not in modele.profils.index:
        raise HTTPException(404, f"Aucun profil pour l'apprenant {user_id}")

    ligne = modele.profils.loc[user_id]
    cats = {c.replace("cat_", ""): round(float(v), 4)
            for c, v in ligne.filter(like="cat_").items() if v > 0}
    nivs = {n.replace("niv_", ""): round(float(v), 4)
            for n, v in ligne.filter(like="niv_").items() if v > 0}

    return ProfilApprenant(
        user_id=user_id,
        groupe=modele.groupe(user_id),
        inscriptions=len(modele.suivies(user_id)),
        categorie_dominante=max(cats, key=cats.get),
        repartition_categories=dict(sorted(cats.items(), key=lambda kv: -kv[1])),
        repartition_niveaux=nivs,
    )


@router.get("/{user_id}", response_model=Suggestions,
            summary="Formations suggérées pour un apprenant")
def suggestions(user_id: int, k: int = Query(5, ge=1, le=20)):
    """
    Les `k` formations les plus pertinentes pour un apprenant.

    Un apprenant sans historique — inconnu du modèle, ou jamais inscrit — reçoit les
    formations les plus suivies. La réponse indique toujours quelle méthode a servi,
    pour que l'interface puisse le dire à l'utilisateur.
    """
    if not modele.suivies(user_id):
        return Suggestions(
            user_id=user_id,
            methode="populaire",
            groupe=None,
            deja_suivies=0,
            suggestions=_reponse(_populaires(k)),
        )

    return Suggestions(
        user_id=user_id,
        methode="hybride",
        groupe=modele.groupe(user_id),
        deja_suivies=len(modele.suivies(user_id)),
        suggestions=_reponse(_scores_hybrides(user_id).nlargest(k)),
    )


app.include_router(router)
