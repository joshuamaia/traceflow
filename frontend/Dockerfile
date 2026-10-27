# syntax=docker/dockerfile:1
# Build context: repository root (see docker-compose.yml)

# ---------- build stage ----------
FROM node:24-alpine AS build
WORKDIR /app

# Optional extra CA certificates (corporate proxies / TLS inspection). docker/certs is empty by default.
COPY docker/certs/ /tmp/certs/
RUN cat /tmp/certs/*.crt > /tmp/extra-ca.pem 2>/dev/null || true; \
    if [ -s /tmp/extra-ca.pem ]; then echo "export NODE_EXTRA_CA_CERTS=/tmp/extra-ca.pem" > /tmp/ca-env; else : > /tmp/ca-env; fi

COPY frontend/package.json frontend/package-lock.json ./
RUN . /tmp/ca-env && npm ci --no-audit --no-fund
COPY frontend/ ./
RUN . /tmp/ca-env && npx ng build --configuration production

# ---------- runtime stage ----------
FROM nginx:1.29-alpine
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
