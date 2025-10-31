# -------------------------
# Stage: Build
# -------------------------
FROM node:25-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# -------------------------
# Stage: Runetime
# -------------------------
FROM node:25-alpine
WORKDIR /app
ENV NODE_ENV=production TZ=UTC
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev
COPY --from=builder /app/dist ./dist

ENTRYPOINT ["node", "dist/index.js"]