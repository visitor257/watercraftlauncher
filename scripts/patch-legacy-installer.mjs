import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/mod/src/main/java/dev/mcver/core/LoaderInstaller.java';
const raw = fs.readFileSync(p, 'utf8');
const crlf = raw.includes('\r\n');
let s = crlf ? raw.replace(/\r\n/g, '\n') : raw;

const anchor = `            writeProfile(gameDir, id, profile, log);
            return id;
        } catch (Exception e) {
            log.accept("profile 方式失败（" + e.getMessage() + "），改用 installer");
        }`;
const repl = `            writeProfile(gameDir, id, profile, log);
            return id;
        } catch (Exception e) {
            if (FabricMeta.isLegacy(mc)) {
                // 官方 fabric-installer 把 meta 写死成 meta.fabricmc.net，对 Legacy 只会回 400，白跑一次
                throw new IOException("用 Legacy Fabric 装 " + mc + " 失败：" + e.getMessage(), e);
            }
            log.accept("profile 方式失败（" + e.getMessage() + "），改用 installer");
        }`;

if (!s.includes(anchor)) {
  console.log('MISS: 找不到 Fabric 那段');
  process.exit(1);
}
s = s.replace(anchor, repl);
fs.writeFileSync(p, Buffer.from(crlf ? s.replace(/\n/g, '\r\n') : s, 'utf8'));
console.log('ok');
