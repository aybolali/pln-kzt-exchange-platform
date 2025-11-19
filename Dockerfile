# ===== ЭТАП 1: СБОРКА =====
# Используем образ Maven для сборки проекта
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Копируем pom.xml и скачиваем зависимости отдельно
# (так Docker кеширует этот слой и не скачивает заново при изменении кода)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходный код и собираем JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ===== ЭТАП 2: ЗАПУСК =====
# Используем лёгкий образ только с Java (без Maven)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# ✅ ИСПРАВЛЕНО: Устанавливаем wget для healthcheck (ПЕРЕД созданием пользователя!)
RUN apk add --no-cache wget

# Создаём пользователя для безопасности (не запускаем от root)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем собранный JAR из первого этапа
COPY --from=build /app/target/pln-kzt-exchangeBot-0.0.1-SNAPSHOT.jar app.jar

# Порт, на котором работает приложение
EXPOSE 8080

# Проверка здоровья приложения (теперь wget доступен!)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Команда запуска
ENTRYPOINT ["java", "-jar", "app.jar"]