# 构建上下文 = 仓库根目录
# docker build -t forgeops-runtime:v2 -f runtime/forgeops-paseo-runtime/Dockerfile .
FROM node:22-alpine AS build
WORKDIR /src
RUN corepack enable
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY runtime/forgeops-paseo-runtime/package.json runtime/forgeops-paseo-runtime/
RUN pnpm --filter @forgeops/paseo-runtime install --frozen-lockfile
COPY runtime/forgeops-paseo-runtime runtime/forgeops-paseo-runtime
RUN pnpm --filter @forgeops/paseo-runtime build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /src/runtime/forgeops-paseo-runtime/dist ./dist
COPY --from=build /src/runtime/forgeops-paseo-runtime/node_modules ./node_modules
COPY --from=build /src/runtime/forgeops-paseo-runtime/package.json ./
EXPOSE 7676
ENTRYPOINT ["node", "dist/server.js"]
