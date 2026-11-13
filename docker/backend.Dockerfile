# BaPiOS backend: Spring Boot multi-module jar.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY aurora-contract/pom.xml aurora-contract/pom.xml
COPY aurora-rbac-core/pom.xml aurora-rbac-core/pom.xml
COPY aurora-tool-registry/pom.xml aurora-tool-registry/pom.xml
COPY aurora-agent-runtime/pom.xml aurora-agent-runtime/pom.xml
COPY aurora-retrieval/pom.xml aurora-retrieval/pom.xml
COPY aurora-app-demo/pom.xml aurora-app-demo/pom.xml
RUN mvn -q -B -Dmaven.test.skip=true dependency:go-offline || true
COPY aurora-contract aurora-contract
COPY aurora-rbac-core aurora-rbac-core
COPY aurora-tool-registry aurora-tool-registry
COPY aurora-agent-runtime aurora-agent-runtime
COPY aurora-retrieval aurora-retrieval
COPY aurora-app-demo aurora-app-demo
RUN mvn -q -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 aurora && chown -R aurora:aurora /app
USER aurora
COPY --from=build /build/aurora-app-demo/target/aurora-app-demo-*.jar app.jar
ENV TZ=Asia/Shanghai \
    AURORA_DB_URL=jdbc:mysql://mysql:3306/aurora?useSSL=false\&allowPublicKeyRetrieval=true\&serverTimezone=Asia/Shanghai\&characterEncoding=utf8 \
    AURORA_DB_USER=aurora_app \
    AURORA_DB_PASSWORD=aurora_app_pw \
    AURORA_PG_URL=jdbc:postgresql://pgvector:5432/aurora_vector \
    AURORA_PG_USER=aurora \
    AURORA_PG_PASSWORD=aurora_vector_pw \
    AURORA_MINIO_ENDPOINT=http://minio:9000 \
    AURORA_STORAGE=minio
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
