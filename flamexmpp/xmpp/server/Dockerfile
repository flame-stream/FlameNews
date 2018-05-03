FROM openjdk:8-jre

RUN apt-get update && apt-get install -y --no-install-recommends libssl-dev

RUN cd /usr/lib/x86_64-linux-gnu && ln -s libssl.so.1.0.2 libssl.so.1.0.0 && ln -s libcrypto.so.1.0.2 libcrypto.so.1.0.0

ARG JAR_FILE

ADD target/lib /usr/share/vk-grabber/lib
ADD target/${JAR_FILE} /usr/share/vk-grabber/vk-grabber.jar
ADD target/classes/marnikitta.com.p12 /certs/

ENTRYPOINT ["/usr/bin/java", "-DunitTestEnabled=false", "-jar", "/usr/share/vk-grabber/vk-grabber.jar"]
