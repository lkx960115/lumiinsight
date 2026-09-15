/**
 * 本机原生 esbuild 会卡住无输出。开发服改用 esbuild-wasm 打包 Vue，并代理 /api。
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'
import * as esbuild from 'esbuild-wasm'
import { parse, compileScript, compileTemplate, compileStyle } from '@vue/compiler-sfc'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const srcDir = path.join(webRoot, 'src')
const PORT = 5173
const API_TARGET = { hostname: '127.0.0.1', port: 8080 }

const mime = {
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.woff2': 'font/woff2',
}

let files = new Map()

function vuePlugin() {
  return {
    name: 'vue-sfc',
    setup(build) {
      build.onLoad({ filter: /\.vue$/ }, async (args) => {
        const source = await fs.promises.readFile(args.path, 'utf8')
        const { descriptor, errors } = parse(source, { filename: args.path })
        if (errors.length) {
          return { errors: errors.map((e) => ({ text: e.message, location: e.loc })) }
        }
        const id = `data-v-${crypto.createHash('md5').update(args.path).digest('hex').slice(0, 8)}`
        const hasScoped = descriptor.styles.some((s) => s.scoped)
        let code
        if (descriptor.script || descriptor.scriptSetup) {
          const compiled = compileScript(descriptor, {
            id,
            inlineTemplate: true,
            templateOptions: {
              compilerOptions: { scopeId: hasScoped ? id : undefined },
            },
          })
          code = compiled.content
        } else if (descriptor.template) {
          const t = compileTemplate({
            id,
            filename: args.path,
            source: descriptor.template.content,
            compilerOptions: { scopeId: hasScoped ? id : undefined },
          })
          code = `${t.code}\nexport default { render }`
        } else {
          code = 'export default {}'
        }
        let css = ''
        for (const style of descriptor.styles) {
          const s = compileStyle({
            id,
            filename: args.path,
            source: style.content,
            scoped: Boolean(style.scoped),
          })
          css += s.code
        }
        if (css) {
          code += `\n;(() => { const el = document.createElement('style'); el.textContent = ${JSON.stringify(css)}; document.head.appendChild(el); })();`
        }
        return { contents: code, loader: 'ts', resolveDir: path.dirname(args.path) }
      })
    },
  }
}

async function bundle() {
  const result = await esbuild.build({
    absWorkingDir: webRoot,
    entryPoints: [path.join(srcDir, 'main.ts')],
    bundle: true,
    splitting: true,
    format: 'esm',
    outdir: path.join(webRoot, '.lumi-dev'),
    write: false,
    sourcemap: true,
    plugins: [vuePlugin()],
    alias: { '@': srcDir },
    define: { 'process.env.NODE_ENV': '"development"' },
    loader: { '.css': 'css', '.png': 'file', '.svg': 'file' },
    logLevel: 'info',
  })
  const next = new Map()
  for (const out of result.outputFiles) {
    const name = '/' + path.basename(out.path)
    next.set(name, Buffer.from(out.contents))
  }
  files = next
}

function send(res, status, body, type) {
  res.writeHead(status, { 'Content-Type': type, 'Cache-Control': 'no-store' })
  res.end(body)
}

function proxyApi(req, res) {
  const opts = {
    hostname: API_TARGET.hostname,
    port: API_TARGET.port,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: `${API_TARGET.hostname}:${API_TARGET.port}` },
  }
  const p = http.request(opts, (pr) => {
    res.writeHead(pr.statusCode || 502, pr.headers)
    pr.pipe(res)
  })
  p.on('error', (err) => {
    send(res, 502, JSON.stringify({ code: 'BAD_GATEWAY', message: err.message }), 'application/json')
  })
  req.pipe(p)
}

function indexHtml() {
  const css = files.has('/main.css') ? '<link rel="stylesheet" href="/main.css" />' : ''
  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>灯鉴 LumiInsight</title>
    ${css}
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/main.js"></script>
  </body>
</html>`
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url || '/', `http://127.0.0.1:${PORT}`)
  if (url.pathname.startsWith('/api')) {
    proxyApi(req, res)
    return
  }
  if (url.pathname === '/' || url.pathname === '/index.html' || !path.extname(url.pathname)) {
    send(res, 200, indexHtml(), mime['.html'])
    return
  }
  const buf = files.get(url.pathname)
  if (buf) {
    send(res, 200, buf, mime[path.extname(url.pathname)] || 'application/octet-stream')
    return
  }
  const pub = path.join(webRoot, 'public', path.normalize(url.pathname).replace(/^[/\\]+/, ''))
  if (pub.startsWith(path.join(webRoot, 'public')) && fs.existsSync(pub) && fs.statSync(pub).isFile()) {
    send(res, 200, fs.readFileSync(pub), mime[path.extname(pub)] || 'application/octet-stream')
    return
  }
  send(res, 404, 'not found', 'text/plain')
})

async function main() {
  console.log('灯鉴前端：本机原生 esbuild/Vite 会卡住，改用 WASM 打包（第一次大约半分钟）...')
  await esbuild.initialize()
  await bundle()
  server.on('error', (err) => {
    if (err && err.code === 'EADDRINUSE') {
      console.error('5173 已被占用，前端多半已经在跑。请直接打开 http://127.0.0.1:5173/login ，不要再启一份。')
      process.exit(0)
    }
    console.error(err)
    process.exit(1)
  })
  server.listen(PORT, '127.0.0.1', () => {
    console.log(`已启动 http://127.0.0.1:${PORT}  （接口代理到 http://127.0.0.1:8080）`)
  })
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
