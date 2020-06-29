FROM adoptopenjdk:11-jre-hotspot

ENV WEBTAIL_SLEEP=0 \
    WEBTAIL_URL="" \
    WEBTAIL_LOGSTASH_HOST="" \
    WEBTAIL_LOGSTASH_PORT="" \
    WEBTAIL_PROXY_HOST="" \
    WEBTAIL_PROXY_PORT="" \
    JAVA_OPTS=""

# Add a app user to run our application so that it doesn't need to run as root
RUN adduser --disabled-password --gecos "" --shell /bin/sh app
WORKDIR /home/app

ADD entrypoint.sh entrypoint.sh
RUN chmod 755 entrypoint.sh && chown app:app entrypoint.sh
USER app

ADD *.jar app.jar
ENTRYPOINT ["./entrypoint.sh"]
