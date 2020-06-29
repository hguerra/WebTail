#!/bin/sh

echo "The application will start in ${WEBTAIL_SLEEP}s with args _JAVA_OPTIONS '${_JAVA_OPTIONS}' and JAVA_OPTS '${JAVA_OPTS}' ..." && sleep ${WEBTAIL_SLEEP}
exec java ${JAVA_OPTS} -noverify -XX:+AlwaysPreTouch -Djava.security.egd=file:/dev/./urandom -jar "${HOME}/app.jar" "$WEBTAIL_URL" "$WEBTAIL_LOGSTASH_HOST" "$WEBTAIL_LOGSTASH_PORT" "$WEBTAIL_PROXY_HOST" "$WEBTAIL_PROXY_PORT"
