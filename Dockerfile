# syntax = docker/dockerfile:1.4
# 多阶段构建:第一阶段用 Clojure CLI 打 uberjar,第二阶段只带 JRE 运行。
# 前端产物需在构建镜像前先执行 `npx shadow-cljs release app`(或在 CI 里加一个 node 阶段)。
FROM clojure:temurin-21-tools-deps-bookworm-slim AS build

WORKDIR /app
COPY . .
RUN clojure -T:build all

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/target/rouyi-standalone.jar /app/rouyi-standalone.jar

ENV PORT=3000
EXPOSE 3000

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/rouyi-standalone.jar"]
