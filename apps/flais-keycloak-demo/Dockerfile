# -----------------------------
# Stage: Build
# -----------------------------
FROM node:25-alpine AS build

WORKDIR /app

COPY . .

RUN npm install && npm run build

# -----------------------------
# Stage: Runtime
# -----------------------------
FROM nginx:alpine-slim

COPY --from=build /app/dist /usr/share/nginx/html
COPY /nginx.conf /etc/nginx/conf.d/default.conf
COPY env.sh /docker-entrypoint.d/env.sh

EXPOSE 80

RUN chmod +x /docker-entrypoint.d/env.sh