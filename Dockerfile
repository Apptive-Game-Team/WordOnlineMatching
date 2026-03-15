# Stage 1: Build
FROM gradle:8-jdk21 AS build

WORKDIR /app

# Copy gradle build files first to cache dependency downloads
COPY build.gradle settings.gradle ./
COPY gradle gradle

# Resolve and cache all compile/runtime/test dependencies before copying source
RUN gradle dependencies --no-daemon --configuration compileClasspath \
    && gradle dependencies --no-daemon --configuration runtimeClasspath \
    && gradle dependencies --no-daemon --configuration testCompileClasspath

# Copy source code and build
COPY src src

RUN gradle clean build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
