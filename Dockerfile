# 1단계: 빌드 환경 (Java 25 JDK)
FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

# 실행 권한 부여 및 Jar 빌드 (테스트 제외)
RUN chmod +x gradlew && ./gradlew clean bootJar -x test --no-daemon

# 2단계: 실행 환경 (Java 25 JRE)
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

# 빌드 단계에서 생성된 Jar 파일을 복사
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]