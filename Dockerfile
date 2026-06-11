# HoldOff production container
# Portable replacement for Render: works on Railway, Fly.io, Heroku container deploys, and most VPS/container hosts.
FROM node:20-slim AS runtime

ENV NODE_ENV=production
WORKDIR /app

# Install production dependencies first for better cache behavior.
COPY package*.json ./
RUN npm ci --omit=dev && npm cache clean --force

# Copy application source.
COPY . .

# Use the non-root node user included in the official image.
USER node

# The platform supplies PORT. 3000 is local fallback only.
EXPOSE 3000

CMD ["npm", "start"]
