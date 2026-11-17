# 문제 파일

레슨마다 문제 파일 하나(`<레슨 slug>.yaml`). 학습 앱 [콕](https://github.com/wanja727/kok)이 시작할 때 이 파일들을 가져간다.

- 형식: [콕의 문제 파일 형식](https://github.com/wanja727/kok/blob/main/docs/content-format.md)
- 검사: PR마다 `Check questions` CI가 콕의 검사기로 형식을 확인한다. 직접 돌리려면 콕 저장소를 옆에 받고 `gradle -p ../kok/backend checkContent -PcontentDir=$PWD/content/questions`
- 상태: 새 문제는 `draft`. 근거를 확인한 뒤 `reviewed`로 바꾸면 콕에 보인다
- 키(`key`)는 한 번 정하면 바꾸지 않는다. 바꾸면 다른 문제로 보고 풀이 기록이 이어지지 않는다

## 노션에서 옮긴 문제 (2026-11-17)

처음 233개는 노션 문제 DB에서 옮겼다. 키 `q-xxxxxxxx`는 노션 페이지 id의 끝 8자리다.

- 형태: 설명형·비교형·상황형 → `descriptive`, 객관식·수정안 고르기 → `multiple_choice`, 코드 예측 → `code_predict`, OX → `ox`, 라인 고르기·버그 찾기 → `line_pick`
- 개념은 아직 레슨마다 하나(`core`)다. 지연 확인을 개념 단위로 더 잘게 하려면 나눈다
- 줄 고르기 코드에서는 줄 번호를 뗐다 (화면이 붙인다)
- 체크리스트만 있던 연습 문제는 해설을 "체크리스트 항목이 모범 답안의 요점이다"로 두었다
- 노션에서는 모두 "초안"이었다. 개발 단계라 2026-11-17에 모두 `reviewed`로 바꿨다 (내용 검수 없이 상태만). 내용을 고치면 같은 PR에서 바로잡는다
