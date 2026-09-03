# Frontend Angular — construit puis servi par nginx.
#
# Multi-etage : node compile, nginx sert. L'image finale ne contient ni node, ni
# node_modules, ni les sources — seulement les fichiers statiques produits.
FROM node:20-alpine AS build
WORKDIR /app

# package*.json d'abord : tant que les dependances ne changent pas, Docker reutilise
# la couche npm install au lieu de la refaire a chaque modification du code.
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

FROM nginx:alpine
RUN apk add --no-cache gettext

# Angular 18 ecrit dans dist/<projet>/browser/, pas dans dist/<projet>/ : c'est la
# sortie « application builder ». Copier le dossier parent donnerait a nginx un
# repertoire contenant browser/ au lieu d'un index.html.
COPY --from=build /app/dist/skillup-front/browser /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf.template
EXPOSE 80

# L'URL de la gateway est compilee en dur dans le bundle Angular
# (environment.apiUrl = http://localhost:9090). Plutot que de reconstruire l'image
# pour la changer, nginx la remplace a la volee dans les reponses (sub_filter), avec
# la valeur de GATEWAY_URL injectee ici par envsubst.
ENV GATEWAY_URL="http://localhost:9090"
CMD ["sh", "-c", "envsubst '$GATEWAY_URL' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
