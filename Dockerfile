# HoldOff production container — manus-web (React + tRPC + Express)
# Node 22 LTS, multi-stage build for minimal image size.

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM node:22-slim AS builder
WORKDIR /app

# Install pnpm
RUN npm install -g pnpm@9

# Copy manus-web source
COPY manus-web/ ./

# Install all deps (including devDeps needed for build)
RUN pnpm install --frozen-lockfile

# Build client (Vite) + server bundle
RUN pnpm build

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM node:22-slim AS runtime
ENV NODE_ENV=production
WORKDIR /app

RUN npm install -g pnpm@9

COPY --from=builder /app/dist ./dist
COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/pnpm-lock.yaml ./pnpm-lock.yaml

RUN pnpm install --prod --frozen-lockfile && pnpm store prune

USER node

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
  CMD node -e "require('http').get('http://localhost:' + (process.env.PORT || 3000) + '/api/trpc/auth.me?batch=1&input=%7B%7D', (r) => process.exit(r.statusCode < 500 ? 0 : 1)).on('error', () => process.exit(1))"

CMD ["node", "dist/index.js"]
