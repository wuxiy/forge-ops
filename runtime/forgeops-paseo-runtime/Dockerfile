# 构建上下文 = 仓库根目录
# docker build -t forgeops-runtime:v2 -f runtime/forgeops-paseo-runtime/Dockerfile .
FROM node:22-alpine AS build
WORKDIR /src
RUN corepack enable
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY runtime/forgeops-paseo-runtime/package.json runtime/forgeops-paseo-runtime/
RUN pnpm --filter @forgeops/paseo-runtime install --frozen-lockfile
COPY runtime/forgeops-paseo-runtime runtime/forgeops-paseo-runtime
RUN pnpm --filter @forgeops/paseo-runtime build \
    && pnpm --filter @forgeops/paseo-runtime deploy --prod --legacy /deploy \
    && cp -R runtime/forgeops-paseo-runtime/dist /deploy/dist

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /deploy ./
EXPOSE 7676
ENTRYPOINT ["node", "dist/server.js"]
