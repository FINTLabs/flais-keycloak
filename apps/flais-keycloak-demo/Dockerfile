FROM node:24-alpine AS build

WORKDIR /app

COPY . .

RUN npm install && npm run build

FROM nginx:alpine-slim

COPY --from=build /app/dist /usr/share/nginx/html
COPY ./nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80