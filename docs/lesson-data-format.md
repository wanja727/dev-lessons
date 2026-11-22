# 레슨 자료 파일 형식

**상태: 초안이다 (2026-11-23).** 동작 확인 유형 레슨 세 개로 만들었다. 나머지 세 유형의 기준 레슨을 이 형식으로 쓰면서 고치고, 그 뒤에 굳힌다 (`docs/quality-plan.md` 진행 순서 5번).

레슨 본문은 `.astro` 페이지를 직접 쓰지 않고 자료 파일의 칸을 채워서 만든다. 페이지의 구역과 모양은 틀이 정한다.

| 무엇 | 어디 |
| --- | --- |
| 자료 파일 | `site/src/content/lesson-pages/<슬러그>.yaml` |
| 칸의 검사 | `site/src/content.config.ts`의 `lessonPages`. 빠진 칸이 있으면 빌드가 실패한다 |
| 페이지 틀 | `site/src/components/lesson/LessonPage.astro` |
| 본문 블록 | `site/src/components/lesson/Blocks.astro` |
| 공통 위젯 | `site/src/components/lesson/WidgetKit.astro` |
| 레슨 전용 위젯 | `site/src/components/lesson-widgets/<슬러그>/<이름>.astro` |
| 주소 | `site/src/pages/lessons/[slug].astro`가 자료 파일마다 `/lessons/<슬러그>/`를 만든다 |

예로 볼 파일: `jpa-n-plus-1.yaml`(기준 레슨), `tx-basics.yaml`(공통 위젯 네 가지를 쓴다).

## 맨 위의 칸

| 칸 | 내용 |
| --- | --- |
| `title`, `unit`, `minutes` | 제목, 단원, 걸리는 시간(분). 레슨 목록(`site/src/content/lessons/<슬러그>.md`)과 같아야 한다. 다르면 빌드가 실패한다 |
| `description` | 검색 결과에 보이는 설명 한 문장 |
| `prerequisites` | 먼저 배울 레슨. `{slug, title}`의 목록. 없으면 `[]` |
| `assume` | "알고 있다고 보는 것" 뒤에 올 글 |
| `links` | 글에서 `link:이름`으로 가리킬 주소 |
| `codes` | 이름 붙인 코드 블록. 본문과 예측이 이름으로 가져다 쓴다. 같은 코드를 강조만 바꿔 여러 번 보여 주려면 이름을 달리해 둘 넣는다 |
| `predict` | 먼저 예측해 보기. `title`, `text`, `code`(코드 이름), `label`, `options`(`{value, label}`), `answer`(정답의 `value`), `storageKey`(고른 값을 기억할 이름), `wrong`(틀렸을 때의 문장. `{value}`와 `{label}`이 고른 것으로 바뀐다) |
| `questions` | 핵심 질문 3~5개. 아래 "질문 하나" |
| `interview` | 면접 질문 `question`과 모범 답 `model`(`{label, text}`의 목록. 정의, 원리, 주의할 점, 경험) |
| `verified` | 검증 구역. `summary`(접힌 제목)와 `body`(본문 블록) |
| `footer` | 근거를 적는 맨 아래 글 |

## 질문 하나

| 칸 | 내용 |
| --- | --- |
| `id` | `q1`, `q2` 순서대로. 문제 파일의 `coreQuestions` 키와 같다 |
| `title` | 질문 문장. 문제 파일의 `coreQuestions`의 `text`와 같다 |
| `conclusion` | 결론 상자. `lead`(답 한 문장)와, `sub`(받치는 문장) 또는 `subList`(받치는 항목들) |
| `says` | 선택. 본문이 말할 것의 목록. 설계서에서 쓴다. 페이지에는 나오지 않고, 문제를 쓸 때 근거로 삼는다 |
| `body` | 본문 블록의 목록. 확인 문제(`quiz`)가 하나 있어야 한다 |
| `recall` | "보지 않고 답하기"의 모범 답 |

틀이 알아서 넣는 것: "질문 N" 표시, 핵심 질문 목록, 진행 표시, "보지 않고 답하기" 구역, 면접 답의 순서 문장.

## 본문 블록

블록 하나는 칸 하나짜리 항목이다.

| 블록 | 내용 |
| --- | --- |
| `p` | 문단 |
| `muted` | 흐린 문단. 덧붙이는 말 |
| `h3` | 작은 제목 |
| `ol`, `ul` | 번호 목록, 점 목록. 글의 목록 |
| `code` | `codes`의 이름. 옆에 `notes`를 두면 코드 아래 설명이 된다. `notes`의 항목은 `{mark, text}`이고, `mark`는 코드에서 `[[ ]]`로 강조한 부분이다. `as: code`를 주면 `mark`에 코드 색을 입힌다. 낱말 하나에만 색을 줄 때는 `as: str`(문자열 색), `as: anno`(어노테이션 색) |
| `prewrap` | 로그처럼 줄일 수 없는 한 줄. 화면 폭에 맞춰 줄을 바꾼다 |
| `table` | `head`(칸 이름들)와 `rows`(행들). 좁은 화면에서는 카드로 쌓인다 |
| `callout` | 강조 상자 |
| `more` | 접어 두는 "더 알아보기". `summary`와 `body`(본문 블록) |
| `ci` | 검증 테스트가 남긴 실제 출력. `key`, `label`, `source` |
| `run` | "저장소에서 이 테스트 돌려 보기". `tests`(테스트 클래스의 전체 이름들), `docker` |
| `quiz` | 장마다 하나 있는 확인 문제. `q`, `options`, `answer`(보기 번호, 1부터), `explain` |
| `next` | "다음 질문:" 뒤에 올 글 |
| `widget` | 위젯. 아래 "위젯" |

위젯은 읽는 폭보다 넓게 쓴다. 그래서 틀이 위젯 앞뒤의 블록을 따로 묶는다. 블록의 순서만 맞게 적으면 된다.

## 글 쓰는 법

글에 쓸 수 있는 표시는 세 가지뿐이다. HTML은 해석하지 않고 글자 그대로 보여 준다.

| 표시 | 뜻 |
| --- | --- |
| `**굵게**` | 굵은 글씨 |
| `` `코드` `` | 코드 조각. `<`와 `>`를 그대로 써도 된다 |
| `[글](주소)` | 링크. 주소 자리에 `lesson:슬러그`를 쓰면 그 레슨으로, `link:이름`을 쓰면 `links`의 주소로 간다 |

`{{predict-result}}`는 예측이 맞았는지 알려 주는 문장이 들어갈 자리다. 예측의 답을 밝히는 문단에 한 번 넣는다.

코드 블록 안의 표시는 `docs/lesson-standard.md`의 "코드"와 같다: `[[강조]]`, 줄 끝의 `⤶`와 `⤷`.

## 위젯

### 공통 위젯

자료만으로 만든다. `kind`로 고른다.

| `kind` | 무엇 | 예 |
| --- | --- | --- |
| `classify` | 상황 분류하기. 상황마다 어느 것에 해당하는지 고른다. `categories`와 `items`(`{text, answer, explain}`, `answer`는 1부터) | `tx-basics`의 질문 5 |
| `toggle` | 켜고 끄며 비교하기. `selector`에서 하나를 고르면 `scenes`에서 그 장면을 보여 준다. `run`이 있으면 실행 버튼을 눌러야 보이고, 그 전에는 `initial`을 보여 준다 | `jpa-n-plus-1`의 질문 2, `tx-basics`의 질문 4, `connection-pool`의 질문 4 |
| `steps` | 한 단계씩 실행하기. 버튼을 누를 때마다 `scenarios`의 다음 장면으로 넘어간다. `buttons: each`면 단계마다 버튼을 따로 두고(`label`), 아니면 "다음 단계" 버튼 하나를 둔다. `selector`가 있으면 시나리오를 고른다 | `tx-basics`의 질문 1과 2 |

장면은 아래 조각으로 이루어진다. 무엇을 어떤 순서로 놓을지는 위젯의 `layout`에 적는다.

| 조각 | 내용 |
| --- | --- |
| `selector` | 고르는 버튼 묶음. `label`과 `options`(`{value, label}`) |
| `buttons` | 실행, 다음 단계, 처음으로 버튼이 놓이는 줄 |
| `duo` | 나란한 두 칸. 칸마다 `label`과 `rows`(`{k, v, mark}`) 또는 `text`. `mark`는 `changed`(바뀜)나 `bad`(문제). `state`는 `active`(강조)나 `dim`(흐리게) |
| `phases` | 구간별 로그. 구간마다 `title`, `count`, `log` |
| `log` | 번호 붙은 로그. `toggle`에서는 줄의 목록, `steps`에서는 그 단계에 더할 한 줄. `{note: 글}`은 번호 없는 줄 |
| `gantt` | 시간 막대. `total`, `axis`(눈금 글 셋), `rows`(`{label, bars}`), 막대는 `{kind, from, to}`. `kind`는 `hold`, `wait`, `fail`, `api` |
| `legend` | 막대 색의 뜻. `{kind, label}`의 목록 |
| `verdict` | 한 줄 정리 |

두 칸 비교와 타임라인은 아직 만들지 않았다. 지금 레슨 세 개에는 쓰이지 않았다. 설계 유형과 개념 설명 유형의 기준 레슨을 쓸 때 만든다.

### 레슨 전용 위젯

공통 위젯으로 안 되는 것만 직접 만든다. 슬라이더로 값을 바꿔 계산 결과를 보는 것, 누르는 순서가 정해지지 않은 것이 그렇다.

```yaml
- widget: {kind: custom, name: jpa-n-plus-1/QuerySim}
```

- 파일은 `site/src/components/lesson-widgets/<슬러그>/<이름>.astro`다. 틀이 `<div class="panel">`로 감싸므로 안쪽 내용만 쓴다
- 스크립트는 `<script is:inline>` 안에 쓰고 `window.Lesson`(`$`, `pressGroup`, `store`)을 쓴다
- 그 위젯만 쓰는 모양은 같은 파일의 `<style is:global>`에 둔다
- 설계서에 왜 공통 위젯으로 안 되는지 적고 승인받는다

## 아직 정하지 않은 것

- **설계서를 어디에 두나.** 지금 검사는 본문까지 다 채운 파일만 받는다. 설계 단계의 파일(질문, 결론, `says`만 채운 것)을 받는 방법은 기준 레슨 세 개를 쓸 때 정한다
- **레슨 목록과 합치기.** 제목, 단원, 시간이 레슨 목록(`site/src/content/lessons`)과 자료 파일 두 곳에 있다. 지금은 다르면 빌드가 실패하게만 했다
- **블록과 위젯의 칸 검사.** 맨 위의 칸과 질문의 칸은 검사하지만, 본문 블록과 위젯 설정의 안쪽은 모르는 블록 이름만 막는다. 틀을 굳힐 때 조인다
- **코드 한 줄의 길이, 가운뎃점 같은 규칙.** 자료 파일을 보는 검사는 아직 없다 (진행 순서 6번)
