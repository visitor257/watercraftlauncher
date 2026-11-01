// 把 watercraft-launcher 的 jar 发到 Modrinth（按 jars-manifest.csv 逐个建 version）
//
//   node --use-system-ca scripts/modrinth-publish.mjs --versions            # 演练（只列要传的）
//   node --use-system-ca scripts/modrinth-publish.mjs --versions --apply    # 真传（默认跳过已存在的文件名）
//   node --use-system-ca scripts/modrinth-publish.mjs --versions forge --apply
//   node --use-system-ca scripts/modrinth-publish.mjs --project watercraftlauncher --versions
//
// token 来源：环境变量 MODRINTH_TOKEN → --token-file <路径>（取第一行非空）
// 需要的 scope：VERSION_CREATE（传版本）、PROJECT_READ（读草稿项目）；建项目/传图标另外加
//   PROJECT_CREATE / PROJECT_WRITE，默认不做（项目已经存在）。
//
// 三个坑：
//   ① 这台机器的 HTTPS 有中间人代理，Node 必须加 `--use-system-ca`，否则 fetch 报
//      UNABLE_TO_VERIFY_LEAF_SIGNATURE。
//   ② 清单 CSV 里 `Fabric,Quilt` 这种字段带逗号，**不能**用 split(',') 硬切（踩过：列整体错位，
//      把 1.14+ 的包误标成 Legacy Fabric）。这里有一个最小 CSV 解析器。
//   ③ `/user` 需要 USER_READ；token 没勾这个 scope 时会回 401 —— 这不代表 token 无效。
//      想验证 token，看 POST /version 是否返回 400（过了鉴权才到参数校验）。
import { readFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, resolve, basename } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const PUB = join(ROOT, '..', 'watercraft-launcher-modrinth');
const MANIFEST = join(PUB, 'jars-manifest.csv');
const ICON = join(PUB, 'icon', 'from-screenshot', 'icon-title-square-512.png');
const BODY = join(PUB, 'modrinth-body.md');
const API = 'https://api.modrinth.com/v2';

const argv = process.argv.slice(2);
const has = (f) => argv.includes(f);
const val = (f) => { const i = argv.indexOf(f); return i >= 0 ? argv[i + 1] : null; };

let TOKEN = process.env.MODRINTH_TOKEN || '';
const tf = val('--token-file');
if (!TOKEN && tf && existsSync(tf)) {
  TOKEN = readFileSync(tf, 'utf8').split(/\r?\n/).map((s) => s.trim()).find(Boolean) || '';
}
const PROJECT = val('--project') || val('--slug') || 'watercraftlauncher';
const DRY = !has('--apply');

const H = { 'User-Agent': 'mcver-publish/1.0 (github.com/visitor257/watercraftlauncher)' };
if (TOKEN) H.Authorization = TOKEN;

// ---------- 最小 CSV 解析（支持引号里的逗号）----------
function parseCsv(text) {
  const rows = [];
  let row = [], field = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') q = false;
      else field += c;
    } else if (c === '"') q = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; }
    else if (c !== '\r') field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  const head = rows.shift();
  return rows.filter((r) => r.length === head.length).map((r) => {
    const o = {};
    head.forEach((k, i) => { o[k] = r[i]; });
    return o;
  });
}
const readManifest = () => parseCsv(readFileSync(MANIFEST, 'utf8'));

// ---------- HTTP（429/5xx 退避重试）----------
async function req(path, opts = {}, attempt = 1) {
  const r = await fetch(API + path, { ...opts, headers: { ...H, ...(opts.headers || {}) } });
  if ((r.status === 429 || r.status >= 500) && attempt <= 5) {
    const wait = Number(r.headers.get('x-ratelimit-reset') || 0) * 1000 || 3000 * attempt;
    console.log(`   HTTP ${r.status}，等 ${Math.round(wait / 1000)}s 重试`);
    await new Promise((s) => setTimeout(s, Math.min(wait, 60000)));
    return req(path, opts, attempt + 1);
  }
  return r;
}
const bodyText = async (r) => { try { const t = await r.text(); try { return JSON.stringify(JSON.parse(t)); } catch { return t; } } catch { return ''; } };

// Modrinth 的 loader 标识
const LOADER_SLUG = {
  fabric: 'fabric', quilt: 'quilt', forge: 'forge', neoforge: 'neoforge',
  'legacy fabric': 'legacy-fabric', 'legacy-fabric': 'legacy-fabric',
};
const toSlugs = (s) => s.split(',').map((x) => x.trim().toLowerCase()).map((x) => LOADER_SLUG[x] || x).filter(Boolean);

async function listVersions() {
  const r = await req(`/project/${PROJECT}/version`);
  if (r.status === 404) { console.log(`project ${PROJECT} 不存在或读不到（草稿项目需要 PROJECT_READ）`); return []; }
  if (!r.ok) throw new Error(`列版本失败 HTTP ${r.status}: ${(await bodyText(r)).slice(0, 300)}`);
  const j = await r.json();
  return Array.isArray(j) ? j : [];
}

// NeoForge 只有 beta 的几个 MC 版本 → version_type=beta
const BETA_MC = new Set(['1.20.3', '1.20.5', '1.21.2', '1.21.6', '1.21.7', '1.21.9']);

async function uploadVersions(filter) {
  if (!TOKEN && !DRY) throw new Error('传版本需要 token：设 MODRINTH_TOKEN 或 --token-file');
  const rows = readManifest().filter((r) => !filter || (r.loader_dir + '/' + r.file).includes(filter));
  // project_id 必须是**真正的 id**（8 位 base62，像 t7mBi72d），不能是 slug：
  // Modrinth 会对 project_id 做 base62 解码，slug 一长就 "Base62 decoding overflowed"（实测踩过）。
  let pid = PROJECT;
  const pr = await req('/project/' + PROJECT);
  if (pr.ok) { const pj = await pr.json(); if (pj.id) pid = pj.id; }
  const existing = await listVersions();
  const done = new Set(existing.flatMap((v) => (v.files || []).map((f) => f.filename)));
  // 文件名 → 已有 version（含它记录的 sha1/sha256），--replace 时用来判断要不要换文件
  const byFile = new Map();
  for (const v of existing) for (const f of (v.files || [])) byFile.set(f.filename, { version: v, file: f });
  console.log(`清单 ${rows.length} 个 / 云端已有 ${existing.length} 个 version（${done.size} 个文件）`);

  let ok = 0, skip = 0, fail = 0, replaced = 0;
  for (const row of rows) {
    const path = join(PUB, 'jars', row.loader_dir, row.file);
    if (!existsSync(path)) { console.log(`   缺文件 ${row.file}`); fail++; continue; }
    // --replace：文件名已存在但内容变了（sha256 不同）→ 删掉旧 version 再建新的。
    // Modrinth 不允许替换 version 里的文件，只能“删旧建新”。
    if (done.has(row.file)) {
      const prev = byFile.get(row.file);
      // Modrinth 的文件哈希是 sha1 + sha512（不一定有 sha256），所以按 sha1 比
      const localSha1 = createHash('sha1').update(readFileSync(path)).digest('hex');
      const remoteSha1 = prev && prev.file && prev.file.hashes ? prev.file.hashes.sha1 : null;
      const same = localSha1 && remoteSha1 && localSha1 === remoteSha1;
      if (!has('--replace') || same) { skip++; continue; }
      if (DRY) { console.log(`[演练] 会替换 ${row.file}（内容变了）`); replaced++; continue; }
      const del = await req('/version/' + prev.version.id, { method: 'DELETE' });
      if (!(del.ok || del.status === 204)) { console.log(`FAIL 删旧 ${row.file} HTTP ${del.status}`); fail++; continue; }
      console.log(`替换 ${row.file}（旧 version 已删）`);
      replaced++;
    }
    const gv = row.game_versions.split(/\s+/).filter(Boolean);
    const isBeta = row.loader_dir === 'neoforge' && gv.some((v) => BETA_MC.has(v));
    // 默认按清单自动判断（beta 加载器 → beta，其余 release）；
    // 想统一压成 alpha 就加 `--type alpha`（这个项目就是这么发的）
    const forced = val('--type');
    const vtype = forced || (isBeta ? 'beta' : 'release');
    const data = {
      name: row.modrinth_name || `${row.loader_dir} ${gv[0]}`,
      version_number: row.jar_version_number,
      changelog: '变更见 https://github.com/visitor257/watercraftlauncher/releases',
      dependencies: [],
      game_versions: gv,
      version_type: vtype,
      loaders: toSlugs(row.modrinth_loaders),
      featured: false,
      project_id: pid,
      file_parts: ['file'],
      primary_file: 'file',
    };
    if (DRY) {
      console.log(`[演练] ${row.file}\n        name="${data.name}"  ver=${data.version_number}  loaders=${data.loaders.join('+')}  gv=${gv.length}  type=${data.version_type}`);
      ok++; continue;
    }
    const fd = new FormData();
    fd.append('data', JSON.stringify(data));
    fd.append('file', new Blob([readFileSync(path)], { type: 'application/java-archive' }), row.file);
    const r = await req('/version', { method: 'POST', body: fd });
    if (r.ok) {
      const j = await r.json();
      console.log(`OK   ${row.file}  (${data.version_type}, ${data.loaders.join('+')}, ${gv.length} 个版本) id=${j.id}`);
      ok++;
    } else {
      console.log(`FAIL ${row.file}  HTTP ${r.status}: ${(await bodyText(r)).slice(0, 300)}`);
      fail++;
    }
    await new Promise((s) => setTimeout(s, 350));
  }
  console.log(`\n完成：成功 ${ok}，已存在跳过 ${skip}，失败 ${fail}`);
  if (fail) process.exitCode = 1;
}

async function createProject() {
  const existing = await req(`/project/${PROJECT}`);
  if (existing.ok) { console.log(`project ${PROJECT} 已存在，跳过`); return; }
  if (!TOKEN || DRY) { console.log(`[演练] 会创建 project ${PROJECT}`); return; }
  const body = {
    slug: PROJECT, title: 'WaterCraftLauncher',
    description: '在游戏里右键物品打开面板：下载/安装 MC 版本、装 Fabric/Forge/NeoForge/Quilt、管账号、启动别的版本。',
    categories: ['utility', 'management'], client_side: 'required', server_side: 'unsupported',
    project_type: 'mod', license_id: 'mit',
    body: existsSync(BODY) ? readFileSync(BODY, 'utf8') : '',
    source_url: 'https://github.com/visitor257/watercraftlauncher',
    issues_url: 'https://github.com/visitor257/watercraftlauncher/issues',
  };
  const r = await req('/project', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  console.log(r.ok ? `建好 project: ${PROJECT}` : `建 project 失败 HTTP ${r.status}: ${(await bodyText(r)).slice(0, 300)}`);
}

async function uploadIcon() {
  if (!existsSync(ICON)) throw new Error('图标不存在: ' + ICON);
  if (!TOKEN || DRY) { console.log('[演练] 会传图标 ' + basename(ICON)); return; }
  const fd = new FormData();
  fd.append('file', new Blob([readFileSync(ICON)], { type: 'image/png' }), basename(ICON));
  const r = await req(`/project/${PROJECT}/icon?ext=png`, { method: 'POST', body: fd });
  console.log(r.ok ? '图标已传' : `传图标失败 HTTP ${r.status}: ${(await bodyText(r)).slice(0, 300)}`);
}

// 批量改版本通道（release/ beta/ alpha）。Modrinth 用 PATCH /version/{id}，只传要改的字段。
//   node --use-system-ca scripts/modrinth-publish.mjs --retag release alpha --apply
async function retag(from, to) {
  if (!TOKEN && !DRY) throw new Error('改通道需要 token（scope 要含 VERSION_WRITE）');
  const vs = await listVersions();
  const hit = vs.filter((v) => v.version_type === from);
  console.log(`版本共 ${vs.length} 个，其中 ${from} 有 ${hit.length} 个${DRY ? '（演练）' : ''}`);
  let ok = 0, fail = 0;
  for (const v of hit) {
    if (DRY) { console.log(`[演练] ${v.name}  ${from} -> ${to}`); ok++; continue; }
    const r = await req(`/version/${v.id}`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version_type: to }),
    });
    if (r.ok || r.status === 204) { console.log(`OK   ${v.name}  ${from} -> ${to}`); ok++; }
    else { console.log(`FAIL ${v.name}  HTTP ${r.status}: ${(await bodyText(r)).slice(0, 200)}`); fail++; }
    await new Promise((s) => setTimeout(s, 250));
  }
  console.log(`\n改完：成功 ${ok}，失败 ${fail}`);
  if (fail) process.exitCode = 1;
}

if (has('--list')) {
  for (const r of readManifest()) console.log(`${r.loader_dir.padEnd(9)} ${r.file.padEnd(52)} ${r.modrinth_loaders.padEnd(14)} ${r.game_versions.split(/\s+/).length} 个版本  ${r.modrinth_name}`);
  process.exit(0);
}
const ri = argv.indexOf('--retag');
if (ri >= 0) {
  const from = argv[ri + 1] || 'release';
  const to = argv[ri + 2] || 'alpha';
  await retag(from, to);
}
if (has('--create-project')) await createProject();
if (has('--icon')) await uploadIcon();
const vi = argv.indexOf('--versions');
if (vi >= 0 || (DRY && ri < 0)) {
  const filter = vi >= 0 && argv[vi + 1] && !argv[vi + 1].startsWith('--') ? argv[vi + 1] : '';
  await uploadVersions(filter);
}
if (DRY) console.log('\n（演练模式：加 --apply 真传；已存在的文件名会自动跳过）');
