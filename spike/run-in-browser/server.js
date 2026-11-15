// 시험용 정적 서버 (CheerpJ는 Range 요청이 필요하다). node server.js
const http = require('http');
const fs = require('fs');
const path = require('path');
const root = __dirname;
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.jar': 'application/java-archive', '.wasm': 'application/wasm' };
http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  const file = path.join(root, url === '/' ? 'index.html' : url);
  if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); return res.end(); }
  const size = fs.statSync(file).size;
  const headers = { 'Content-Type': types[path.extname(file)] || 'application/octet-stream', 'Accept-Ranges': 'bytes' };
  const m = /bytes=(\d*)-(\d*)/.exec(req.headers.range || '');
  if (m) {
    const start = m[1] ? +m[1] : 0, end = m[2] ? Math.min(+m[2], size - 1) : size - 1;
    res.writeHead(206, { ...headers, 'Content-Range': `bytes ${start}-${end}/${size}`, 'Content-Length': end - start + 1 });
    return fs.createReadStream(file, { start, end }).pipe(res);
  }
  res.writeHead(200, { ...headers, 'Content-Length': size });
  fs.createReadStream(file).pipe(res);
}).listen(process.env.PORT || 4810, () => console.log('spike server on ' + (process.env.PORT || 4810)));
