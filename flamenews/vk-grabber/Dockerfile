FROM openjdk:8-jre
MAINTAINER Artem Trofimov <trofimov9artem@gmail.com>

ADD target/lib /usr/share/vk-grabber/lib
ARG JAR_FILE
ADD target/${JAR_FILE} /usr/share/vk-grabber/vk-grabber.jar

ENTRYPOINT /usr/bin/java -jar /usr/share/vk-grabber/vk-grabber.jar "$APP_ID" "$ACCESS_TOKEN" "$JID" "$PASSWORD"
