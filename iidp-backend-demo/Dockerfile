FROM anapsix/alpine-java:8_jdk_unlimited
COPY --from=hengyunabc/arthas:latest /opt/arthas /opt/arthas
ENV TZ Asia/Shanghai
RUN sed -i 's/http\:\/\/dl-cdn.alpinelinux.org/https\:\/\/mirrors.ustc.edu.cn/g' /etc/apk/repositories
RUN apk add tzdata && cp /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo ${TZ} > /etc/timezone \
    && apk del tzdata
RUN apk add --update \
    curl \
    && rm -rf /var/cache/apk/* \
    && mkdir /apps_temp
COPY apps/* /apps_temp/
ADD sie-snest-center-service/target/sie-snest-center-service-1.0-SNAPSHOT.jar /app.jar
ENTRYPOINT ["/bin/sh","-c"]
CMD ["cp -rf /apps_temp/* /apps && mv /apps/apps-dev.json /apps/apps.json && java ${SW_OPTS} -Djava.security.egd=file:/dev/./urandom -Xbootclasspath/a:/config -Dspring.output.ansi.enabled=always -Dfile.encoding=UTF-8 -jar /app.jar"]