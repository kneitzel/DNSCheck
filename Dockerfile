FROM maven:3.9-eclipse-temurin-25 AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build

COPY pom.xml .
COPY dnscheck-core/pom.xml dnscheck-core/
COPY dnscheck-service/pom.xml dnscheck-service/
COPY dnscheck-cli/pom.xml dnscheck-cli/
COPY dnscheck-fx/pom.xml dnscheck-fx/

RUN mvn -B -pl dnscheck-service,dnscheck-cli -am dependency:go-offline

COPY dnscheck-core dnscheck-core
COPY dnscheck-service dnscheck-service
COPY dnscheck-cli dnscheck-cli

RUN mvn -B -pl dnscheck-service,dnscheck-cli -am package -DskipTests -Dquarkus.package.jar.enabled=true \
    -Dquarkus.quinoa.package-manager-install=true -Dquarkus.quinoa.package-manager-install.node-version=26.13.0

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /build/dnscheck-service/target/quarkus-app/ /app/service/
COPY --from=build /build/dnscheck-cli/target/dnscheck-cli-*-SNAPSHOT.jar /app/cli/dnscheck-cli.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh \
    && useradd --system --create-home --home-dir /app --shell /usr/sbin/nologin dnscheck \
    && mkdir -p /data/zones /data/keys \
    && chown -R dnscheck:dnscheck /app /data

USER dnscheck

ENV DNSCHECK_ZONES_DIR=/data/zones \
    DNSCHECK_KEYS_DIR=/data/keys

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["service"]
