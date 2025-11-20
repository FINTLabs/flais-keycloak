ARG KEYCLOAK_VERSION

# -----------------------------
# Stage: FLAIS provider builder
# -----------------------------
FROM gradle:9.2.0-jdk21 AS flais-provider-builder
WORKDIR /flais-provider

COPY settings.gradle.kts ./
COPY build.gradle.kts ./
COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon -PskipFlaisScimServer=true --version

COPY libs/flais-provider/build.gradle.kts libs/flais-provider/

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon -PskipFlaisScimServer=true :libs:flais-provider:dependencies

COPY libs/flais-provider/src libs/flais-provider/src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon -PskipFlaisScimServer=true --configuration-cache build && \
    cp libs/flais-provider/build/libs/*.jar /flais-provider/

#-----------------------------
# Stage: FLAIS scim server builder
# -----------------------------
FROM gradle:9.2.0-jdk21 AS flais-scim-server-builder
WORKDIR /flais-scim-server

COPY settings.gradle.kts ./
COPY build.gradle.kts ./
COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon -PskipFlaisProvider=true --version

COPY libs/flais-scim-server/build.gradle.kts libs/flais-scim-server/

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon -PskipFlaisProvider=true :libs:flais-scim-server:dependencies

COPY libs/flais-scim-server/src libs/flais-scim-server/src

RUN --mount=type=cache,target=/home/gradle/.gradle \
   gradle --no-daemon -PskipFlaisProvider=true --configuration-cache build && \
   cp libs/flais-scim-server/build/libs/*.jar /flais-scim-server/

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

COPY --from=flais-provider-builder /flais-provider/*.jar /opt/keycloak/providers/
COPY --from=flais-scim-server-builder /flais-scim-server/*.jar /opt/keycloak/providers/
COPY --from=flais-theme-builder /flais-theme/dist_keycloak/flais-theme.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build

# -------------------------
# Stage: Keycloak runtime
# -------------------------
FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}
COPY --from=keycloak-builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
