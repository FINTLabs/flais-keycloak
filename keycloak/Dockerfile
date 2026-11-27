FROM quay.io/keycloak/keycloak:26.7.5@sha256:37dbaf6f0722c9ec246335f36e1ef8b2e6cb960f7c27e0d8c615121a3d475a85 AS keycloak-base

# -----------------------------
# Stage: FLAIS Keycloak builder
# -----------------------------
FROM gradle:9.9.2-jdk21@sha256:f8c4f7de6ec4f367cbf7fc46db08129ea6a6e9487995bb705c61fbc20f3bc545 AS flais-keycloak-builder
WORKDIR /flais-keycloak

ENV GRADLE_USER_HOME=/home/gradle/.gradle

COPY settings.gradle.kts Dockerfile ./
COPY build.gradle.kts ./
COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon --version

COPY libs/flais-provider/build.gradle.kts libs/flais-provider/
COPY libs/flais-scim-server/build.gradle.kts libs/flais-scim-server/

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon \
        :libs:flais-provider:dependencies \
        :libs:flais-scim-server:dependencies

COPY libs/flais-provider/src libs/flais-provider/src
COPY libs/flais-scim-server/src libs/flais-scim-server/src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon --configuration-cache \
        :libs:flais-provider:build \
        :libs:flais-scim-server:build \
    && mkdir -p /flais-keycloak-jars \
    && cp libs/flais-provider/build/libs/*.jar /flais-keycloak-jars/ \
    && cp libs/flais-scim-server/build/libs/*.jar /flais-keycloak-jars/

# ---------------------------
# Stage: FLAIS theme builder
# ---------------------------
FROM node:26-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS flais-theme-builder
WORKDIR /flais-theme

RUN apk add --no-cache maven openjdk21-jdk

COPY libs/flais-theme/package.json libs/flais-theme/package-lock.json ./

RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY libs/flais-theme/ ./

RUN --mount=type=cache,target=/root/.npm \
    npm run build-keycloak-theme

# -------------------------
# Stage: Keycloak builder
# -------------------------
FROM keycloak-base AS keycloak-builder
WORKDIR /opt/keycloak

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
ENV KC_EVENT_METRICS_USER_ENABLED=true

COPY --from=flais-keycloak-builder /flais-keycloak-jars/*.jar /opt/keycloak/providers/

COPY --from=flais-theme-builder /flais-theme/dist_keycloak/flais-theme.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build

# -------------------------
# Stage: Keycloak runtime
# -------------------------
FROM keycloak-base
COPY --from=keycloak-builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
