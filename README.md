WebTail
=======

This is a simplest possible implementation of tail -F for a file on the web : 
every 5 seconds it checks whether the content provided in an URL has grown in size
and, if so, retrieves and send the difference to logstash. Usable e.g. for log files available
through an apache.

Build:
```shell script
$ mvn clean compile assembly:single
$ cp -R src/main/docker/* target/
$ cd target
$ docker build -t heitorcarneiro/webtail:0.3 .
```

Usage:
```shell script
$ java -jar WebTail.jar url proxyhost proxyport

or

$ docker run --rm --network="host" -e "WEBTAIL_URL=http://localhost:9000/logs/java/server.log" -e "WEBTAIL_LOGSTASH_HOST=localhost" -e "WEBTAIL_LOGSTASH_PORT=5000" heitorcarneiro/webtail:0.1
```

The proxy arguments are optional.


See [docker-compose](https://github.com/hguerra/WebTail/tree/master/src/main/docker):
```yaml
version: "3"

networks:
  services_internal:
    external: false

services:

  logstash:
    restart: always
    image: docker.elastic.co/logstash/logstash:7.8.0
    environment:
      - INPUT_TCP_PORT=5000
      - INPUT_UDP_PORT=5000
      - INPUT_HTTP_PORT=5001
      - LOGSTASH_DEBUG=false
      - APP_NAME=sample
      - APP_PROFILE=prd
    volumes:
      - "$PWD/config/logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro"
      - "$PWD/config/logstash.yml:/usr/share/logstash/config/logstash.yml:ro"
    ports:
      - "5000:5000"
      - "5000:5000/udp"
      - "5001:5001"
    networks:
      - "services_internal"

  webtail:
    restart: always
    image: heitorcarneiro/webtail:0.3
    environment:
      - WEBTAIL_URL=http://localhost:9000/logs/java/server.log
      - WEBTAIL_LOGSTASH_HOST=logstash
      - WEBTAIL_LOGSTASH_PORT=5000
    networks:
      - "services_internal"
```

Status:
This is only a barebone implementation which I did since
http://www.jibble.org/webtail/ failed me for some unknown reason.
Feel free to extend. :-)
GPL licence.

Hans-Peter Stoerr
http://www.stoerr.net/

Heitor Carneiro
https://github.com/hguerra

