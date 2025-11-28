FROM node:25-slim

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
