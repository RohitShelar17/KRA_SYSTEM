# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build stage
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json* ./
# `npm ci` when there is a lockfile, `npm install` when there is not: a build
# that fails because a lockfile was not committed helps nobody.
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

COPY . .
# Baked in at build time, because Vite inlines it: a container image is per
# environment, not per request.
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ------------------------------------------------------------------ run stage
FROM nginx:alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null || exit 1
