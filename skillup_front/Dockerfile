 FROM node:20-alpine AS build
  WORKDIR /app
  COPY package*.json ./
  RUN npm install
  COPY . .
  RUN npm run build -- --configuration production

  FROM nginx:alpine
  RUN apk add --no-cache gettext
  COPY --from=build /app/dist/skillup-front /usr/share/nginx/html
  COPY nginx.conf /etc/nginx/nginx.conf.template
  EXPOSE 80
  CMD ["sh", "-c", "envsubst '$GATEWAY_URL' < /etc/nginx/nginx.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
