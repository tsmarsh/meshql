FROM alpine:3.19

RUN apk add --no-cache bash curl jq

COPY seed.sh /seed.sh
RUN chmod +x /seed.sh

CMD ["/seed.sh"]
