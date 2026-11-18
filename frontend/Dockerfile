# Matriz SPA image for local use with Docker Compose (see docker-compose.yml):
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
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
