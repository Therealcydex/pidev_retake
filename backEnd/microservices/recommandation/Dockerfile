# Service de recommandation — FastAPI + scikit-learn.
#
# Pas de multi-stage ici, contrairement aux services Java : Python n'a rien a compiler,
# il n'y a donc pas d'etage de build a jeter. L'image reste petite en partant de -slim
# et en n'installant que ce que requirements.txt demande.
FROM python:3.12-slim

WORKDIR /app

# Les dependances d'abord, le code ensuite : tant que requirements.txt ne change pas,
# Docker reutilise la couche d'installation. Sans cette separation, la moindre
# modification de main.py reinstallerait pandas et scikit-learn.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

EXPOSE 8000

# Deux directions a ne pas confondre :
#
#   SKILLUP_DB_URL, EUREKA_URL  — du conteneur VERS l'exterieur. « localhost » y designe
#                                 le conteneur lui-meme, d'ou host.docker.internal pour
#                                 joindre MySQL et Eureka restes sur l'hote.
#
#   EUREKA_INSTANCE_HOST        — l'inverse : l'adresse que ce service publie dans
#                                 Eureka, celle que la gateway utilisera pour l'appeler.
#                                 Avec -p 8000:8000 et une gateway sur l'hote, cette
#                                 adresse est « localhost » vue par la gateway. Sur un
#                                 reseau Docker partage, ce serait le nom du conteneur.
ENV SKILLUP_DB_URL="mysql+pymysql://root@host.docker.internal:3306/pidev_formation?charset=utf8mb4" \
    EUREKA_URL="http://host.docker.internal:8761/eureka" \
    EUREKA_INSTANCE_HOST="localhost" \
    PORT=8000

ENTRYPOINT ["python", "main.py"]
