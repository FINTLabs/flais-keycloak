FROM gradle:8.9-jdk21 AS spi-builder
WORKDIR /workspace

COPY ./libs/flais-provider /workspace

RUN gradle --no-daemon clean build

FROM quay.io/keycloak/keycloak:latest AS keycloak-builder
WORKDIR /opt/keycloak

COPY --from=spi-builder /workspace/build/libs/*.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build