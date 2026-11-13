# BaPiOS frontend: Vite build served by nginx, /api proxied to the backend.
FROM node:20-alpine AS build
WORKDIR /app
RUN npm install -g pnpm@10 --no-fund --no-audit
COPY aurora-ui-shell/package.json aurora-ui-shell/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY aurora-ui-shell/ ./
RUN pnpm build

FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
