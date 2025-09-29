ARG KEYCLOAK_VERSION=26.3.5

# Flais provider builder
FROM gradle:9.1.0-jdk21 AS flais-provider-builder
WORKDIR /flais-provider

COPY ./libs/flais-provider/build.gradle.kts \
    /flais-provider/

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon --refresh-dependencies

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon --configuration-cache \
    :libs:flais-provider:dependencies || true

COPY ./libs/flais-provider/src /flais-provider/src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon --configuration-cache build

# Flais theme builder
FROM node:24-alpine AS flais-theme-builder
WORKDIR /flais-theme

RUN apk add maven openjdk21-jdk

COPY ./libs/flais-theme/package.json ./libs/flais-theme/package-lock.json /flais-theme/

RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY ./libs/flais-theme/ /flais-theme/

RUN --mount=type=cache,target=/root/.npm \
    npm run build-keycloak-theme

# Keycloak builder
FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION} AS keycloak-builder
WORKDIR /opt/keycloak

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

COPY --from=flais-provider-builder /flais-provider/build/libs/*.jar /opt/keycloak/providers/
COPY --from=flais-theme-builder /flais-theme/dist_keycloak/flais-theme.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build

# Keycloak runtime
FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}
COPY --from=keycloak-builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]