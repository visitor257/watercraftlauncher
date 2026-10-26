// 本地回环 HTTP 服务：给 Net 的 POST/GET 做一次真实往返检查（不碰外网）。
import http from 'node:http';

const seen = [];
const srv = http.createServer((req, res) => {
  let body = '';
  req.on('data', (c) => { body += c; });
  req.on('end', () => {
    seen.push({ method: req.method, url: req.url, auth: req.headers.authorization, ct: req.headers['content-type'], len: body.length });
    console.log(`[srv] ${req.method} ${req.url} auth=${req.headers.authorization} ct=${req.headers['content-type']} body=${body}`);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: true, method: req.method, got: body.length }));
  });
});
srv.listen(18080, '127.0.0.1', () => console.log('[srv] listening 18080'));
setTimeout(() => { srv.close(); console.log('[srv] closed, 收到 ' + seen.length + ' 个请求'); }, 20000);
