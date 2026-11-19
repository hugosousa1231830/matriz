# Matriz SPA image for Docker Compose (compose.yaml) and Render (render.yaml):
# production build served by nginx, which also proxies /api to the backend.

FROM node:22.23.5-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Optional extra CA certificate (e.g. a TLS-inspecting corporate proxy), given
# as the build secret extra_ca; empty by default.
RUN --mount=type=secret,id=extra_ca,target=/run/secrets/extra_ca \
    NODE_EXTRA_CA_CERTS=/run/secrets/extra_ca npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27.5-alpine
# nginx.conf.template becomes /etc/nginx/conf.d/default.conf at start-up, with
# these defaults for Docker Compose; render.yaml overrides both. The literal
# $http_host is the nginx variable with the client's Host header.
ENV MATRIZ_API_UPSTREAM=http://backend:8080 \
    MATRIZ_API_HOST_HEADER=\$http_host
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
