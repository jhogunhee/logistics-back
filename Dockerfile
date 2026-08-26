# ===== Build stage =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 의존성 레이어 캐시: pom.xml만 먼저 복사해 소스 변경 시 재다운로드 방지
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src src
RUN mvn -q -B package -DskipTests

# ===== Run stage =====
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render free 인스턴스(512MB)에서 힙이 컨테이너 한도를 넘지 않도록 제한
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
