// 레슨과 문제에 적은 출처 주소가 열리는지 본다.
// 바깥 사이트의 사정으로 실패할 수 있어서 PR을 막는 검사에 넣지 않고, 주기로 돌린다 (.github/workflows/sources.yml).
//
//   node check-sources.mjs
//
// 없는 페이지(404, 410)와 없는 주소만 오류로 본다. 접근을 막거나(403, 429) 잠시 안 되는 것(5xx, 시간 초과)은 알리기만 한다.
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parse } from "yaml";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const QUESTIONS = join(ROOT, "content", "questions");
const LESSONS = join(ROOT, "site", "src", "content", "lessons");
const TIMEOUT_MS = 20_000;
const PARALLEL = 6;

/** 문제 파일의 source와 레슨 메타데이터의 sources를 모은다. 주소 → 그 주소를 쓰는 파일들 */
export function collectSources(questionsDir = QUESTIONS, lessonsDir = LESSONS) {
  const found = new Map();
  const add = (url, where) => {
    if (typeof url !== "string" || !/^https?:\/\//.test(url)) return;
    const key = url.split("#")[0];
    found.set(key, [...(found.get(key) ?? []), where]);
  };
  for (const name of readdirSync(questionsDir).filter((n) => n.endsWith(".yaml"))) {
    const file = parse(readFileSync(join(questionsDir, name), "utf8"));
    for (const q of file?.questions ?? []) add(q.source, `content/questions/${name}`);
  }
  for (const name of readdirSync(lessonsDir).filter((n) => n.endsWith(".md"))) {
    const front = readFileSync(join(lessonsDir, name), "utf8").match(/^---\r?\n([\s\S]*?)\r?\n---/);
    if (!front) continue;
    for (const url of parse(front[1])?.sources ?? []) add(url, `site/src/content/lessons/${name}`);
  }
  for (const [url, files] of found) found.set(url, [...new Set(files)]);
  return found;
}

/** @returns {"ok" | "broken" | "unknown"} */
export function classify(status) {
  if (status >= 200 && status < 400) return "ok";
  if (status === 404 || status === 410) return "broken";
  return "unknown";
}

async function probe(url) {
  try {
    const response = await fetch(url, {
      redirect: "follow",
      signal: AbortSignal.timeout(TIMEOUT_MS),
      headers: { "user-agent": "Mozilla/5.0 (link-check)", accept: "text/html,*/*" },
    });
    return { verdict: classify(response.status), detail: `HTTP ${response.status}` };
  } catch (e) {
    // 없는 주소(DNS 실패)는 깨진 것으로, 그 밖의 연결 문제는 알 수 없음으로 본다
    const code = e?.cause?.code ?? e?.name ?? "error";
    return { verdict: code === "ENOTFOUND" ? "broken" : "unknown", detail: String(code) };
  }
}

async function main() {
  const sources = [...collectSources()];
  const results = new Array(sources.length);
  let next = 0;
  await Promise.all(
    Array.from({ length: PARALLEL }, async () => {
      while (next < sources.length) {
        const i = next++;
        results[i] = await probe(sources[i][0]);
      }
    }),
  );

  let broken = 0;
  let unknown = 0;
  sources.forEach(([url, files], i) => {
    const { verdict, detail } = results[i];
    if (verdict === "ok") return;
    if (verdict === "broken") broken++;
    else unknown++;
    console.log(`${verdict === "broken" ? "오류" : "확인 못 함"} ${url} (${detail})`);
    for (const f of files) console.log(`    ${f}`);
  });
  console.log(`출처 주소 ${sources.length}개, 열리지 않음 ${broken}개, 확인 못 함 ${unknown}개`);
  process.exit(broken > 0 ? 1 : 0);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main();
}
