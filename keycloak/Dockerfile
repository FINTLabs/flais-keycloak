ARG KEYCLOAK_VERSION

# -----------------------------
# Stage: FLAIS Keycloak builder
# -----------------------------
FROM gradle:9.3.0-jdk21 AS flais-keycloak-builder
WORKDIR /flais-keycloak

ENV GRADLE_USER_HOME=/home/gradle/.gradle

COPY settings.gradle.kts ./
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
    && cp libs/flais-provider/build/libs/*.jar / \
    && cp libs/flais-scim-server/build/libs/*.jar /

# ---------------------------
# Stage: FLAIS theme builder
# ---------------------------
FROM node:25-alpine AS flais-theme-builder
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
FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION} AS keycloak-builder
WORKDIR /opt/keycloak

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

COPY --from=flais-keycloak-builder /*.jar /opt/keycloak/providers/

COPY --from=flais-theme-builder /flais-theme/dist_keycloak/flais-theme.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build

# -------------------------
# Stage: Keycloak runtime
# -------------------------
FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}
COPY --from=keycloak-builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
