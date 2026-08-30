# Partie MLA — notes de soutenance

**Besoin :** *analyser des profils pour suggérer les formations les plus pertinentes.*

---

## En une phrase

Chaque apprenant laisse une trace — les formations auxquelles il s'est inscrit. On en
construit un **profil**, on regroupe les apprenants semblables, puis on recommande à
chacun les formations suivies par ses semblables et celles dont le contenu ressemble à
ce qu'il a déjà choisi.

---

## Questions probables et réponses

**« C'est quoi votre besoin MLA ? »**
> Analyser les profils des apprenants pour leur suggérer les formations les plus
> pertinentes.

**« Vos données viennent d'où ? »** *(la question qui revient toujours)*
> Elles sont générées par un script versionné, `ml/generate_dataset.py`, avec une graine
> fixée à 42 — donc reproductible. 200 apprenants, 58 formations, 1 595 inscriptions.
> Elles sont ensuite injectées dans la vraie base MySQL, pour que les identifiants
> correspondent aux comptes de l'application.

**« Quel algorithme avez-vous utilisé ? »**
> Trois briques : **K-Means** pour segmenter les profils, **TF-IDF + similarité cosinus**
> pour la proximité de contenu entre formations, et un **filtrage collaboratif** sur la
> matrice d'inscriptions. La recommandation finale est **hybride** : elle combine le
> contenu et le collaboratif.

**« Comment avez-vous choisi le nombre de groupes k ? »**
> Par le **score de silhouette**, calculé pour k de 2 à 15 — pas à l'œil sur la courbe du
> coude. Le maximum est atteint à **k = 9**.

**« Vous avez fait une ACP ? »**
> Oui, pour la visualisation. **8 composantes** sont nécessaires pour atteindre 80 % de la
> variance : la projection en deux dimensions sert donc à voir la structure, pas à
> modéliser dessus.

**« Pourquoi ne pas avoir standardisé les données avant l'ACP ? »**
> Parce que les 13 variables du profil sont **déjà des proportions**, entre 0 et 1, qui
> somment à 1 par bloc. Les standardiser donnerait à une catégorie rare le même poids
> qu'à une catégorie très suivie, ce qui déformerait les distances entre apprenants.

**« Comment savez-vous que ça marche ? »**
> Trois vérifications successives, chacune avant l'étape suivante :
> 1. le **profil** retrouve la préférence réelle de l'apprenant dans **78,5 %** des cas
>    (97,5 % si l'on accepte ses deux catégories préférées) ;
> 2. le **regroupement** retrouve **9 des 10 catégories** sans les avoir jamais vues,
>    avec une pureté de **73 %** ;
> 3. la **recommandation** obtient une précision@5 de **28,1 %** contre **6,7 %** pour la
>    référence — soit **4,2 fois mieux**.

**« Pourquoi comparer aux formations les plus populaires ? »**
> Parce que c'est la solution qu'on obtient sans aucun modèle. Un modèle qui ne la bat pas
> ne sert à rien. Et l'exploration montre que la popularité n'est pas concentrée — les 5
> formations les plus suivies ne pèsent que 12,7 % des inscriptions — donc la barre est
> franchissable, mais pas triviale.

**« 28 % de précision, c'est faible non ? »**
> Non, compte tenu du protocole. Chaque apprenant n'a que **2,6 formations en moyenne**
> dans l'échantillon de test : même une recommandation parfaite plafonnerait à
> **2,6/5 ≈ 52 %**. On atteint donc un peu plus de la moitié du maximum atteignable. Le
> chiffre le plus parlant est le **rappel : 55 %** des formations réellement suivies
> ressortent dans seulement 5 suggestions, sur un catalogue de 58.

**« C'est intégré à l'application ? »**
> Oui. Une API **FastAPI** recharge le modèle et l'expose derrière la gateway :
> `GET /recommandations/{user_id}?k=5`. Rien n'est réappris au moment de la requête, les
> similarités sont précalculées.

---

## La question qui peut piquer

**« Vos données sont inventées, donc vos résultats ne valent rien ? »**

> Ce qui est démontré, c'est que **la chaîne complète et le protocole tiennent**. Les
> données contiennent **20 % de bruit délibéré**, et le modèle a retrouvé **9 des 10
> catégories sans jamais les voir**. Sur de vrais utilisateurs, moins réguliers, les
> scores seraient plus bas — mais la méthode et le code sont identiques, seule la source
> des données change.

L'assumer avant qu'on ne le soulève est bien plus solide que de le défendre après.

---

## Limites à annoncer soi-même

- **Silhouette à 0,19**, ce qui est faible. C'est attendu : chaque apprenant a une
  catégorie secondaire, donc les groupes se chevauchent réellement. La silhouette mesure
  la séparation géométrique, la **pureté de 73 %** mesure la pertinence métier — ici c'est
  la seconde qui compte, et il vaut mieux le dire que présenter 0,19 comme un bon score.
- **Une catégorie sur dix n'a pas son groupe.** On garde le k choisi par la mesure plutôt
  qu'un k choisi pour flatter le résultat.
- **Démarrage à froid** : la précision est divisée par deux pour les apprenants ayant 3
  inscriptions ou moins (17 % contre 32 %). Ce n'est pas un défaut du modèle mais une
  limite structurelle — sans historique, il n'y a rien à analyser. L'API bascule alors sur
  les formations populaires, et le signale dans sa réponse (`methode: "populaire"`).
- **Le taux d'erreur du profil suit un U** : les apprenants très actifs se trompent autant
  que les inactifs, parce qu'une catégorie ne contient que 4 à 10 formations — celui qui
  en suit 14 a forcément débordé. C'est la taille du catalogue, pas le modèle.

---

## Chiffres à retenir

| | |
|---|---|
| Apprenants / formations / inscriptions | 200 / 58 / 1 595 |
| Matrice remplie à | 13,8 % |
| Fidélité du profil | 78,5 % (97,5 % sur 2 catégories) |
| Groupes (silhouette) | k = 9, pureté 73 %, 9/10 catégories |
| ACP | 8 composantes pour 80 % |
| Précision@5 / Rappel@5 (hybride) | 28,1 % / 55,3 % |
| Référence populaire | 6,7 % → **×4,2** |

---

## Démonstration

**Sans rien lancer :** ouvrir `ml/pidev_retake.html` — l'analyse complète, graphiques et
résultats inclus, dans le navigateur. Aucun Python, aucun Jupyter.

**En direct :**

1. Arrêter Jupyter — il occupe le port **8888**, celui du config-server (ils ne peuvent
   pas tourner ensemble).
2. Démarrer config-server → USER → Formation → **gateway** (redémarrage nécessaire : la
   route `/recommandations/**` est récente).
3. Double-cliquer `ml\start_api.bat`.
4. `http://localhost:9090/recommandations/1001?k=5`

Se connecter comme `apprenant001` … `apprenant200`, mot de passe `Test1234!`.

**Exemple à montrer — l'apprenant 1001 :** son profil est à 50 % Réseaux et 25 %
Cybersécurité ; comme la catégorie Réseaux ne contient que 4 formations et qu'il les a
toutes suivies, le modèle bascule sur la Cybersécurité. C'est exactement ce que
`/recommandations/profil/1001` permet d'expliquer à l'utilisateur.

---

## Les fichiers

| Fichier | Rôle |
|---|---|
| `generate_dataset.py` | génère le jeu de données (graine 42) |
| `seed_database.py` → `seed_database.sql`, `seed_users.sql` | injection dans MySQL |
| `data/*.csv` | entrée du notebook, identique à la base |
| `pidev_retake.ipynb` | l'analyse — 58 cellules exécutées |
| `pidev_retake.html` | la même chose, lisible sans Jupyter |
| `modele/recommandation.joblib` | le modèle chargé par l'API |
| `api/main.py` | le service FastAPI |
| `start_api.bat` | lanceur (utilise le Python d'Anaconda) |
