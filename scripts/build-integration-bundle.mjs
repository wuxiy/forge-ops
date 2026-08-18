#!/usr/bin/env node
/**
 * 打包 @forgeops/feedback-react 为自包含单文件 ESM bundle（react 外部化），
 * 用于跨仓库集成（目标项目不处于本 workspace、无法使用 workspace:* 协议时）。
 *
 * 产物：sdk/forgeops-feedback-bundle/forgeops-feedback.mjs + .d.ts
 * 用法：目标项目将两个文件复制到 src/forgeops/，然后
 *   import { initForgeOpsFeedback } from './forgeops/forgeops-feedback.mjs'
 */
import { build } from 'esbuild'
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const outDir = path.join(root, 'sdk/forgeops-feedback-bundle')
const outFile = path.join(outDir, 'forgeops-feedback.mjs')

const commit = execSync('git rev-parse --short HEAD').toString().trim()
const version = JSON.parse(fs.readFileSync(path.join(root, 'sdk/forgeops-feedback-react/package.json'), 'utf8')).version

fs.mkdirSync(outDir, { recursive: true })

await build({
  entryPoints: [path.join(root, 'sdk/forgeops-feedback-react/src/index.tsx')],
  bundle: true,
  format: 'esm',
  platform: 'browser',
  target: ['es2019'],
  external: ['react'],
  outfile: outFile,
  banner: {
    js: `/* @forgeops/feedback-bundle v${version} (build ${commit}) - self-contained ESM, react external.\n * Source: forge-ops monorepo sdk/forgeops-feedback-{core,dom,react}. Sync by re-running scripts/build-integration-bundle.mjs */`,
  },
  logLevel: 'info',
})

// .d.ts：react 壳入口的类型直接引用源文件类型声明（目标项目只需这一个文件）
fs.copyFileSync(path.join(root, 'sdk/forgeops-feedback-bundle/forgeops-feedback.d.ts.src'), path.join(outDir, 'forgeops-feedback.d.ts'))
console.log(`bundle -> ${outFile}`)
