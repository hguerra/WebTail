#!/bin/bash

mvn clean compile assembly:single
cp -R src/main/docker/* target/
cd target
/usr/bin/docker build -t heitorcarneiro/webtail:0.6 .
/usr/bin/docker push heitorcarneiro/webtail:0.6
