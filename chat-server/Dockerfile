# 基于 OpenJDK 8
FROM openjdk:8-jdk-slim

# 设置工作目录
WORKDIR /app

# 复制jar包
COPY target/chat-server-1.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]