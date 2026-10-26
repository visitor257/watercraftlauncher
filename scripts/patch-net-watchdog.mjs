// 给 Net.download 加「硬超时看门狗」：DNS 解析不受 setConnectTimeout 约束，
// 网络里遇到黑洞主机时 in.read() 也能绕过 readTimeout，实测会把整个安装卡死一小时。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/mod/src/main/java/dev/mcver/core/Net.java';
const raw = fs.readFileSync(p, 'utf8');
const crlf = raw.includes('\r\n');
let s = crlf ? raw.replace(/\r\n/g, '\n') : raw;

const oldBody = `        HttpURLConnection c = open(url, connectMs, readMs);
        try {
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code + "  " + url);
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
            return dst;
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            throw e;
        } finally {
            c.disconnect();
        }
    }`;

const newBody = `        // 硬超时看门狗：connect/read 超时都管不住 DNS 解析，也管不住某些「半死」连接 ——
        // 实测过一次 1.20.5 的库卡了整整一小时。这里把整个传输放到工作线程，
        // 到点直接 disconnect 并抛错，交给上层重试。
        final HttpURLConnection[] holder = new HttpURLConnection[1];
        final IOException[] err = new IOException[1];
        Thread worker = new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = open(url, connectMs, readMs);
                holder[0] = c;
                int code = c.getResponseCode();
                if (code / 100 != 2) throw new IOException("HTTP " + code + "  " + url);
                try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            } catch (IOException e) {
                err[0] = e;
            } finally {
                if (c != null) c.disconnect();
            }
        }, "mcver-dl");
        worker.setDaemon(true);
        worker.start();

        long hardMs = Math.max(90_000L, (long) connectMs + 2L * readMs);
        try {
            worker.join(hardMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (worker.isAlive()) {
            HttpURLConnection c = holder[0];
            if (c != null) c.disconnect();
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            throw new IOException("下载超时（" + (hardMs / 1000) + "s 没读完）  " + url);
        }
        if (err[0] != null) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            throw err[0];
        }
        Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
        return dst;
    }`;

if (!s.includes(oldBody)) {
  console.log('MISS: Net.download 结构变了');
  process.exit(1);
}
s = s.replace(oldBody, newBody);
fs.writeFileSync(p, Buffer.from(crlf ? s.replace(/\n/g, '\r\n') : s, 'utf8'));
console.log('Net.download 已加看门狗');
