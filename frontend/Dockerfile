# File: frontend/Dockerfile
# Purpose: Build the typed React client and serve immutable assets through an API proxy.
# Stages: build validates the lockfile and bundle; runtime is an unprivileged web server.
FROM node:24.18.0-alpine AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN cd frontend && npm ci
COPY docs/openapi.yaml ./docs/openapi.yaml
COPY frontend ./frontend
RUN cd frontend && npm run verify

FROM nginxinc/nginx-unprivileged:1.31.6-alpine AS runtime
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html
EXPOSE 8080
