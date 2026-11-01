// 把 watercraft-launcher 发到 Modrinth：建 project（可选）→ 传图标 → 按清单逐个建 version
//
//   node scripts/modrinth-publish.mjs                     # 演练：只打印要做什么（不需要 token）
//   node scripts/modrinth-publish.mjs --create-project     # 建 project（需要 token）
//   node scripts/modrinth-publish.mjs --icon               # 传图标
//   node scripts/modrinth-publish.mjs --versions           # 传全部 85 个 version
//   node scripts/modrinth-publish.mjs --versions fabric    # 只传文件路径含 fabric 的
//   node scripts/modrinth-publish.mjs --list               # 看清单
//
// token 来源（按顺序）：环境变量 MODRINTH_TOKEN → --token-file <路径>（取第一行非空）
// 需要的 scope：PROJECT_CREATE（建 project）、PROJECT_WRITE（改图标）、VERSION_CREATE（传版本）
//
// 两个坑：
//   ① 这台机器的 HTTPS 被中间人代理拦，Node 要加 `--use-system-ca` 才连得上（脚本会自己提示）。
//   ② 传 version 是 multipart：data 段是 JSON、file 段是二进制。Modrinth 有速率限制（会回 429 +
//      X-Ratelimit-Reset），这里按响应头等待后重试。
import { readFileSync, existsSync } from 'node:fs';
import { join, resolve, basename } from 'node:path';
import { execFileSync } from 'node:child_process';

const ROOT = resolve(import.meta.dirname, '..');
const PUB = join(ROOT, '..', 'watercraft-launcher-modrinth'); // 素材目录（仓库外）
const MANIFEST = join(PUB, 'jars-manifest.csv');
const ICON = join(PUB, 'icon', 'from-screenshot', 'icon-title-square-512.png');
const BODY = join(PUB, 'modrinth-body.md');
const API = 'https://api.modrinth.com/v2';

const argv = process.argv.slice(2);
const has = (f) => argv.includes(f);
const val = (f) => { const i = argv.indexOf(f); return i >= 0 ? argv[i + 1] : null; };

const tokenFile = val('--token-file');
let TOKEN = process.env.MODRINTH_TOKEN || '';
if (!TOKEN && tokenFile && existsSync(tokenFile)) {
  TOKEN = readFileSync(tokenFile, 'utf8').split(/\r?\n/).map((s) => s.trim()).find(Boolean) || '';
}
const SLUG = val('--slug') || 'watercraft-launcher';
const DRY = !has('--apply');

const H = { 'User-Agent': 'mcver-publish/1.0 (github.com/visitor257/watercraftlauncher)' };
if (TOKEN) H.Authorization = TOKEN;

// ---------- 清单 ----------
function readManifest() {
  const lines = readFileSync(MANIFEST, 'utf8').split(/\r?\n/).filter(Boolean);
  const head = lines.shift().split(',');
  return lines.map((l) => {
    const c = l.split(',');
    const o = {};
    head.forEach((k, i) => { o[k] = c[i]; });
    return o;
  });
}

// ---------- HTTP（带 429/5xx 重试）----------
async function req(path, opts = {}, attempt = 1) {
  const r = await fetch(API + path, { ...opts, headers: { ...H, ...(opts.headers || {}) } });
  if (r.status === 429 || r.status >= 500) {
    if (attempt > 5) throw new Error(`${path} -> HTTP ${r.status}（重试 5 次仍失败）`);
    const wait = Number(r.headers.get('x-ratelimit-reset') || 0) * 1000 || 3000 * attempt;
    console.log(`   ${r.status}，等 ${Math.round(wait / 1000)}s 后重试`);
    await new Promise((s) => setTimeout(s, Math.min(wait, 60000)));
    return req(path, opts, attempt + 1);
  }
  return r;
}
async function jsonOf(r) {
  const t = await r.text();
  try { return JSON.parse(t); } catch { return t; }
}

// ---------- 子命令 ----------
async function listVersions() {
  const r = await req(`/project/${SLUG}/version`);
  if (r.status === 404) { console.log(`project ${SLUG} 还不存在`); return []; }
  if (!r.ok) throw new Error(`列版本失败 HTTP ${r.status}: ${JSON.stringify(await jsonOf(r)).slice(0, 300)}`);
  const j = await jsonOf(r);
  return Array.isArray(j) ? j : [];
}

async function createProject() {
  const existing = await req(`/project/${SLUG}`);
  if (existing.ok) { console.log(`project ${SLUG} 已存在，跳过创建`); return await jsonOf(existing); }
  if (!TOKEN) {
    if (DRY) { console.log('[演练] 会创建 project:', SLUG, '（title=Water Craft Launcher, license=MIT）'); return null; }
    throw new Error('建 project 需要 token：设 MODRINTH_TOKEN 或 --token-file');
  }
  const body = {
    slug: SLUG,
    title: 'Water Craft Launcher',
    description: '在游戏里右键物品打开面板：下载/安装 MC 版本、装 Fabric/Forge/NeoForge/Quilt、管账号、启动别的版本。',
    categories: ['utility', 'management'],
    client_side: 'required',
    server_side: 'unsupported',
    project_type: 'mod',
    license_id: 'mit',
    body: existsSync(BODY) ? readFileSync(BODY, 'utf8') : '见 https://github.com/visitor257/watercraftlauncher',
    source_url: 'https://github.com/visitor257/watercraftlauncher',
    issues_url: 'https://github.com/visitor257/watercraftlauncher/issues',
  };
  if (DRY) { console.log('[演练] 会创建 project:', JSON.stringify({ slug: SLUG, title: body.title, categories: body.categories, license: 'MIT' })); return null; }
  const r = await req('/project', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const j = await jsonOf(r);
  if (!r.ok) throw new Error(`建 project 失败 HTTP ${r.status}: ${JSON.stringify(j).slice(0, 500)}`);
  console.log(`建好 project: ${j.slug} (id=${j.id})`);
  return j;
}

async function uploadIcon() {
  if (!existsSync(ICON)) throw new Error('图标不存在: ' + ICON);
  if (DRY) { console.log('[演练] 会传图标:', basename(ICON)); return; }
  const fd = new FormData();
  fd.append('file', new Blob([readFileSync(ICON)], { type: 'image/png' }), basename(ICON));
  const r = await req(`/project/${SLUG}/icon?ext=png`, { method: 'POST', body: fd });
  if (!r.ok) throw new Error(`传图标失败 HTTP ${r.status}: ${JSON.stringify(await jsonOf(r)).slice(0, 300)}`);
  console.log('图标已传');
}

// NeoForge 只有 beta 的那几个版本 → version_type=beta
const BETA_MC = new Set(['1.20.3', '1.20.5', '1.21.2', '1.21.6', '1.21.7', '1.21.9']);

async function uploadVersions(filter) {
  if (!TOKEN && !DRY) throw new Error('传版本需要 token：设 MODRINTH_TOKEN 或 --token-file');
  const rows = readManifest().filter((r) => !filter || (r.loader_dir + '/' + r.file).includes(filter));
  console.log(`清单里 ${rows.length} 个文件要处理`);
  const existing = await listVersions();
  const done = new Set(existing.flatMap((v) => (v.files || []).map((f) => f.filename)));
  console.log(`project 里已有 ${existing.length} 个 version（${done.size} 个文件）`);

  let ok = 0, skip = 0, fail = 0;
  for (const row of rows) {
    const path = join(PUB, 'jars', row.loader_dir, row.file);
    if (!existsSync(path)) { console.log(`  缺文件 ${row.file}`); fail++; continue; }
    if (done.has(row.file)) { skip++; continue; }
    const gv = row.game_versions.split(/\s+/);
    const isBeta = row.loader_dir === 'neoforge' && gv.some((v) => BETA_MC.has(v));
    const data = {
      name: `${row.loader_dir[0].toUpperCase()}${row.loader_dir.slice(1)} ${gv[0]}${gv.length > 1 ? '~' + gv[gv.length - 1] : ''}`,
      version_number: row.jar_version_number,
      changelog: '见 https://github.com/visitor257/watercraftlauncher/releases',
      dependencies: [],
      game_versions: gv,
      version_type: isBeta ? 'beta' : 'release',
      loaders: row.modrinth_loaders.split(','),
      featured: false,
      project_id: SLUG,
      file_parts: ['file'],
      primary_file: 'file',
    };
    if (DRY) { console.log(`[演练] ${row.file} -> name="${data.name}" loaders=${data.loaders} gv=${gv.length} type=${data.version_type}`); ok++; continue; }

    const fd = new FormData();
    fd.append('data', JSON.stringify(data));
    fd.append('file', new Blob([readFileSync(path)], { type: 'application/java-archive' }), row.file);
    const r = await req('/version', { method: 'POST', body: fd });
    if (r.ok) {
      const j = await jsonOf(r);
      console.log(`OK   ${row.file}  (${data.version_type}, ${data.loaders.join('+')}, ${gv.length} 个版本) id=${j.id}`);
      ok++;
    } else {
      console.log(`FAIL ${row.file}  HTTP ${r.status}: ${JSON.stringify(await jsonOf(r)).slice(0, 300)}`);
      fail++;
    }
    await new Promise((s) => setTimeout(s, 400)); // 别打满速率限制
  }
  console.log(`\n完成：成功 ${ok}，已存在跳过 ${skip}，失败 ${fail}`);
  if (fail) process.exitCode = 1;
}

// ---------- 入口 ----------
if (has('--list')) {
  for (const r of readManifest()) console.log(`${r.loader_dir.padEnd(9)} ${r.file.padEnd(52)} loaders=${r.modrinth_loaders} gv=${r.game_versions.split(/\s+/).length}`);
  process.exit(0);
}
// --versions 后面可以跟一个过滤子串（不带 -- 的那个 token）
const vi = argv.indexOf('--versions');
const vfilter = vi >= 0 && argv[vi + 1] && !argv[vi + 1].startsWith('--') ? argv[vi + 1] : '';
if (!TOKEN && !DRY) {
  console.error('没有 token：设 $env:MODRINTH_TOKEN 或加 --token-file <路径>');
  process.exit(2);
}
if (has('--create-project') || DRY) await createProject();
if (has('--icon') || DRY) await uploadIcon();
if (vi >= 0 || DRY) await uploadVersions(vfilter);
if (DRY) console.log('\n（演练模式；确认无误后把 --apply 加上、并显式指定 --create-project / --icon / --versions）');
