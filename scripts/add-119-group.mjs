// 给 compat/1.19 的 McVerClient 加创造栏（v0 FabricItemGroupBuilder），并改注释。
// 用 node 处理换行，避免 PowerShell 的 CRLF/LF 假设踩坑。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/mod/src/compat/1.19/java/dev/mcver/McVerClient.java';
let s = fs.readFileSync(p, 'utf8');

const anchor = '        Config.load();';
const block = `        Config.load();

        // ---- 创造栏：1.19 ~ 1.19.2 的 fabric-api 没有 ItemGroupEvents（v1 是 1.19.3 才有），
        //      只能用 v0 的 FabricItemGroupBuilder 自建一个「Water Craft Launcher」栏。
        FabricItemGroupBuilder.create(new Identifier(McVerMod.MOD_ID, "items"))
                .icon(() -> new ItemStack(McVerMod.DOWNLOAD_TOOL))
                .appendItems(stacks -> stacks.add(new ItemStack(McVerMod.DOWNLOAD_TOOL)))
                .build();`;

if (s.includes('FabricItemGroupBuilder.create')) {
  console.log('已经加过创造栏');
} else if (s.includes(anchor)) {
  s = s.replace(anchor, block);
  fs.writeFileSync(p, Buffer.from(s, 'utf8'));
  console.log('创造栏已加');
} else {
  console.log('MISS: Config.load() 锚点');
  process.exit(1);
}

// 注释里的版本号
s = fs.readFileSync(p, 'utf8');
s = s.replace('1.19.4 兼容层：客户端入口。', '1.19 ~ 1.19.2 兼容层：客户端入口。');
s = s.replace(/UseItemCallback` 的返回值在 1\.19\.4 还是[\s\S]*?（1\.21\.2 才简化成 `ActionResult`）。/,
  '与 1.19.3/1.19.4 那份的唯一差别是创造栏用 v0 的 FabricItemGroupBuilder（见下）。');
fs.writeFileSync(p, Buffer.from(s, 'utf8'));
console.log('注释已更新');
