// 给自检钩子加一行「当前屏幕类名」日志。
// 起因：1.21.9 / 1.21.10 的自检（MCVER_OPEN_GUI）在真窗口里没触发，但线程栈显示游戏在正常跑主循环。
// 只靠猜没用，先把 currentScreen 到底是什么打出来。
// 主源码和 1.21.9 compat 里的 McVerClient 各有一份，两处都改。
import fs from 'node:fs';

const marker = `                    if (done || !(client.currentScreen instanceof TitleScreen)) return;`;
const debug = `                    if (client.currentScreen != lastScreen) {
                        lastScreen = client.currentScreen;
                        McVerMod.LOGGER.info("mcver: cur=" + (lastScreen == null ? "null" : lastScreen.getClass().getName()));
                    }
                    if (done || !(client.currentScreen instanceof TitleScreen)) return;`;

const fieldMarker = `            ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
                private boolean done;`;
const fieldNew = `            ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
                private boolean done;
                private Object lastScreen;`;

for (const p of [
  'D:/Development/watercraftlauncher/mod/src/main/java/dev/mcver/McVerClient.java',
  'D:/Development/watercraftlauncher/mod/src/compat/1.21.9/java/dev/mcver/McVerClient.java',
]) {
  let s = fs.readFileSync(p, 'utf8');
  const crlf = s.includes('\r\n');
  if (crlf) s = s.replace(/\r\n/g, '\n');
  if (s.includes('mcver: cur=')) {
    console.log(`${p.split('/').slice(-3).join('/')}: 已有，跳过`);
    continue;
  }
  if (!s.includes(fieldMarker) || !s.includes(marker)) {
    console.log(`MISS: ${p}`);
    process.exit(1);
  }
  s = s.replace(fieldMarker, fieldNew).replace(marker, debug);
  if (crlf) s = s.replace(/\n/g, '\r\n');
  fs.writeFileSync(p, Buffer.from(s, 'utf8'));
  console.log(`${p.split('/').slice(-3).join('/')}: 已加`);
}
