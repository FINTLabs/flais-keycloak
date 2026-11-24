FROM node:25-slim@sha256:81db02c4b671288a03915da9534dbd54f96d0e7c24d80ccc54f5b36b2e684370

WORKDIR /app

RUN npm install -g scimverify

ENV BASE_URL=""
ENV CONFIG=""
ENV BEARER_TOKEN=""

CMD sh -c 'scimverify \
  --base-url "$BASE_URL" \
  --config "$CONFIG" \
  --skip-tls-verification \
  --auth-header "Bearer $BEARER_TOKEN"'
