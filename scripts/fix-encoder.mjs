import fs from 'node:fs';
for (const f of ['mod/src/main/java/dev/mcver/core/Auth.java',
                 'mod/src/main/java/dev/mcver/core/Modrinth.java']) {
  let s = fs.readFileSync(f, 'utf8');
  const before = s;
  s = s.split('URLEncoder.encode(s, "UTF-8")').join('dev.mcver.core.Jdk.urlEncode(s)');
  if (s !== before) fs.writeFileSync(f, Buffer.from(s, 'utf8'));
  console.log((s !== before ? '改了 ' : '没改 ') + f);
}
