# 설계서: 단일 책임과 개방 폐쇄 원칙

품질 계획 3단계에서 **설계 유형**이 되는지 보려고 쓴 설계서다. 본문은 아직 쓰지 않았다. 문제는 `srp-ocp.yaml`에 있다.

| 항목 | 내용 |
| --- | --- |
| 로드맵 | O3. 객체지향 설계와 패턴 |
| 유형 | 설계 |
| 선수 | O2 결합도와 응집도 |
| 전제 | 자바 21. 인터페이스와 구현(J7)을 안다 |
| 예제 | 쇼핑몰의 주문 결제. 등급(VIP, GOLD)에 따라 할인하고 영수증을 메일로 보낸다 |

## 레슨 전체에서 쓰는 코드

레슨은 코드 하나를 두 번 고친다. 문제의 코드도 이것과 같은 모양으로 쓴다.

**[코드 A] 처음 코드.** 한 메서드가 할인 계산, 영수증 문구 만들기, 메일 보내기를 다 한다. 메일 서버에 접속해서 보내는 코드는 `MailSender` 안에 있다.

```java
class OrderService {
    MailSender mail;
    int pay(Order order) {
        Grade grade = order.grade();
        int price = order.price();
        if (grade == Grade.VIP) {
            price = price * 90 / 100;
        } else if (grade == Grade.GOLD) {
            price = price * 95 / 100;
        }
        String text =
            "결제 금액: " + price + "원";
        mail.send(order.email(), text);
        return price;
    }
}
```

**[코드 B] 바뀌는 이유마다 나눈 코드.** 질문 2에서 만든다.

```java
class DiscountCalculator {
    int calculate(Order order) {
        Grade grade = order.grade();
        int price = order.price();
        if (grade == Grade.VIP) {
            return price * 90 / 100;
        } else if (grade == Grade.GOLD) {
            return price * 95 / 100;
        }
        return price;
    }
}

class ReceiptWriter {
    String write(int price) {
        return "결제 금액: " + price + "원";
    }
}

class OrderService {
    DiscountCalculator calculator;
    ReceiptWriter writer;
    MailSender mail;
    int pay(Order order) {
        int price =
            calculator.calculate(order);
        String text = writer.write(price);
        mail.send(order.email(), text);
        return price;
    }
}
```

**[코드 C] 등급마다 다른 계산을 인터페이스로 뺀 코드.** 질문 4에서 만든다. `GoldDiscount`는 `VipDiscount`와 같은 모양이고 95를 곱한다.

```java
interface DiscountPolicy {
    boolean supports(Grade grade);
    int apply(int price);
}

class VipDiscount implements DiscountPolicy {
    public boolean supports(Grade grade) {
        return grade == Grade.VIP;
    }
    public int apply(int price) {
        return price * 90 / 100;
    }
}

class DiscountCalculator {
    List<DiscountPolicy> policies;
    int calculate(Order order) {
        Grade grade = order.grade();
        int price = order.price();
        for (DiscountPolicy p : policies) {
            if (p.supports(grade)) {
                return p.apply(price);
            }
        }
        return price;
    }
}
```

**[조립 코드]** 정책 목록을 만들어 `DiscountCalculator`에 넣는 곳이다. 프로그램이 시작할 때 한 번 실행된다.

```java
var policies = List.of(
    new VipDiscount(),
    new GoldDiscount());
```

## 먼저 예측해 보기

[코드 A]를 보여 주고 묻는다. "다음 세 요구 가운데 `OrderService`를 고쳐야 하는 것은 몇 개일까요?" (VIP 할인율 변경, 영수증 문구 변경, 메일 서버 주소 변경). 답은 2개이고 질문 1에서 확인한다.

## 핵심 질문과 결론

| | 질문 | 결론 (첫 줄) | 받치는 문장 (둘째 줄) |
| --- | --- | --- | --- |
| q1 | 단일 책임 원칙은 무엇인가요? | 클래스가 바뀌는 이유는 하나여야 한다는 원칙이에요. | 바뀌는 이유는 그 변경을 요구하는 쪽(마케팅팀, 고객지원팀)으로 세요. 메서드가 하나여야 한다는 뜻이 아니에요. |
| q2 | 책임이 섞여 있으면 어떤 문제가 생기고, 어떻게 나누나요? | 한 가지 이유로 고칠 때 상관없는 기능의 코드까지 건드리게 돼요. 바뀌는 이유마다 클래스를 나눠요. | 나눈 뒤에는 요구 하나에 클래스 하나만 고쳐요. |
| q3 | 개방 폐쇄 원칙은 무엇인가요? | 기능을 더할 때 있던 코드를 고치지 않고 새 코드를 더해서 할 수 있어야 한다는 원칙이에요. | 확장에 열려 있다는 것은 새 기능을 더할 수 있다는 뜻이고, 수정에 닫혀 있다는 것은 있던 코드를 고치지 않는다는 뜻이에요. |
| q4 | 있던 코드를 고치지 않고 어떻게 기능을 더하나요? | 바뀌는 부분을 인터페이스로 빼고, 새 기능은 그 인터페이스의 새 구현 클래스로 더해요. | 쓰는 쪽은 인터페이스만 알아서, 구현이 늘어도 고칠 것이 없어요. |
| q5 | 개방 폐쇄 원칙을 지키면 어떤 변경이든 있던 코드를 고치지 않고 할 수 있나요? | 아니요. 인터페이스로 빼 둔 방향의 변경에만 닫혀 있어요. | 새 등급을 더하는 변경에는 닫혀 있지만, 인터페이스에 메서드를 더하는 변경에는 구현 클래스를 모두 고쳐야 해요. |

결론의 첫 줄만 이어 읽으면 레슨 요약이 된다. 질문은 앞 질문의 답에서 나온다: 원칙 하나(q1) → 어기면 생기는 일과 고치는 법(q2) → 나눈 코드에 남은 문제와 두 번째 원칙(q3) → 고치는 법(q4) → 그 방법의 한계(q5).

## 질문마다 본문이 말할 것

문제는 여기에 적은 것만 묻는다.

### q1. 단일 책임 원칙은 무엇인가요?

- 단일 책임 원칙(SRP)은 클래스가 바뀌는 이유가 하나여야 한다는 원칙이다
- 바뀌는 이유는 그 변경을 요구하는 쪽으로 센다. 할인 규칙은 마케팅팀이, 영수증 문구는 고객지원팀이 바꾸라고 한다
- [코드 A]의 `OrderService`는 할인율이 바뀔 때도, 영수증 문구가 바뀔 때도 고쳐야 한다. 바뀌는 이유가 둘 이상이다
- 메일 서버 주소가 바뀌면 `MailSender`를 고친다. `OrderService`는 고치지 않는다
- 책임이 하나라는 것은 메서드나 필드가 하나라는 뜻이 아니다
- **위젯: 상황 분류하기.** 요구 카드(할인율 변경, 문구 변경, 메일 서버 주소 변경)를 누르면 [코드 A]에서 고쳐야 하는 줄이 강조되고, 그 요구를 낸 쪽이 표시된다

### q2. 책임이 섞여 있으면 어떤 문제가 생기고, 어떻게 나누나요?

- 책임이 섞인 클래스는 한 가지 이유로 고칠 때 상관없는 기능의 코드도 같은 메서드 안에서 함께 건드리게 된다
- [코드 A]에서 영수증 문구가 맞는지 테스트하려면 `pay()`를 불러야 한다. 할인 계산과 메일 보내기가 함께 실행된다
- 나누는 기준: 같은 이유로 바뀌는 코드끼리 모으고, 다른 이유로 바뀌는 코드는 나눈다
- [코드 B]에서 할인율이 바뀌면 `DiscountCalculator`만, 영수증 문구가 바뀌면 `ReceiptWriter`만 고친다
- [코드 B]에서 영수증 문구는 `ReceiptWriter.write()`만 불러 테스트할 수 있다
- `OrderService`에는 세 객체를 순서대로 부르는 일이 남는다
- **위젯: 두 칸 비교.** 왼쪽은 [코드 A], 오른쪽은 [코드 B]. 요구를 고르면 양쪽에서 고쳐야 하는 클래스가 강조되고 "고치는 클래스 수"가 표시된다

### q3. 개방 폐쇄 원칙은 무엇인가요?

- 개방 폐쇄 원칙(OCP)은 기능을 더할 때 있던 코드를 고치지 않고 새 코드를 더해서 할 수 있어야 한다는 원칙이다
- 확장에 열려 있다: 새 기능을 더할 수 있다. 수정에 닫혀 있다: 있던 코드를 고치지 않는다
- [코드 B]의 `DiscountCalculator`는 등급을 `if`와 `else if`로 나눈다. SILVER 등급 3% 할인을 더하려면 `calculate()` 안에 `else if`를 더해야 한다. 있던 메서드를 고치는 것이다
- 등급이 늘 때마다 같은 메서드를 다시 고친다
- 고치는 것을 빠뜨리면 SILVER 주문은 오류 없이 할인이 빠진 원래 금액이 나온다 (10,000원 주문이면 10000)
- **위젯: 한 단계씩 실행하기.** SILVER 주문 10,000원이 `calculate()`의 `if`, `else if`를 차례로 지나 마지막 `return price`에 닿는 것을 한 줄씩 보여 준다

### q4. 있던 코드를 고치지 않고 어떻게 기능을 더하나요?

- 등급마다 다른 계산을 `DiscountPolicy` 인터페이스로 뺀다. 등급 하나의 할인이 구현 클래스 하나다
- [코드 C]의 `DiscountCalculator`는 `DiscountPolicy`만 안다. `VipDiscount` 같은 구현 클래스의 이름은 코드에 나오지 않는다
- SILVER를 더할 때 하는 일: `Grade`에 `SILVER`를 더한다, `SilverDiscount` 클래스를 새로 만든다, [조립 코드]의 목록에 `new SilverDiscount()`를 넣는다
- `DiscountCalculator`와 있던 구현 클래스(`VipDiscount`, `GoldDiscount`)는 고치지 않는다
- 새 클래스를 만들고 목록에 넣지 않으면 SILVER 주문은 원래 금액이 나온다 (10000). 목록에 넣으면 9700이 나온다
- 고치는 곳이 없어지는 것이 아니라, 계산 코드 밖의 한 곳(조립 코드)으로 모인다
- **위젯: 두 칸 비교.** "SILVER 등급 더하기" 버튼을 누르면 [코드 B]와 [코드 C]에서 각각 고친 파일과 새로 만든 파일이 표시된다

### q5. 개방 폐쇄 원칙을 지키면 어떤 변경이든 있던 코드를 고치지 않고 할 수 있나요?

- [코드 C]는 새 등급을 더하는 변경에만 닫혀 있다
- `DiscountPolicy`에 메서드를 더하면(예: 할인 이름을 돌려주는 `String name()`) 구현 클래스를 모두 고쳐야 한다. default 메서드를 쓰지 않으면, 고치지 않은 구현 클래스에서 컴파일 오류가 난다
- 구현이 둘이면 인터페이스 1개와 구현 2개, 파일 3개를 고친다
- 메서드의 모양을 바꾸는 변경(돌려주는 타입, 받는 값)도 인터페이스와 구현을 모두 고쳐야 한다
- 어느 방향의 변경에 닫을지는 인터페이스를 만들 때 정해진다. 그래서 실제로 자주 바뀌는 곳에 적용한다
- **위젯: 상황 분류하기.** 변경 요구 카드를 "새 클래스로 된다"와 "있던 코드를 고쳐야 한다"로 나눠 보고, 누르면 고쳐야 하는 파일이 표시된다

## 테스트로 확인할 주장

설계 유형의 검증은 "요구를 바꿨을 때 바뀌는 클래스 수와 테스트 결과"다. 아래처럼 하면 기계로 확인할 수 있다.

| 주장 | 확인하는 방법 |
| --- | --- |
| [코드 B]에서 `calculate()`를 고치지 않으면 SILVER 10,000원 주문은 10000이 나온다 | 코드를 돌려 반환값을 본다 |
| [코드 C]에서 `SilverDiscount`를 목록에 넣지 않으면 10000, 넣으면 9700이 나온다 | 코드를 돌려 반환값을 본다 |
| [코드 C]에 SILVER를 더할 때 `DiscountCalculator`, `VipDiscount`, `GoldDiscount`는 바뀌지 않는다 | 요구를 반영하기 전과 후의 코드를 패키지 둘에 나란히 두고, 테스트가 두 패키지의 같은 이름 파일을 비교한다 (패키지 선언 줄은 뺀다) |
| [코드 B]에 SILVER를 더하면 `DiscountCalculator`가 바뀐다 | 같은 방법. 파일이 달라야 통과한다 |
| `DiscountPolicy`에 `name()`을 더하고 `GoldDiscount`를 고치지 않으면 컴파일 오류가 난다 | 테스트 안에서 자바 컴파일러(`javax.tools.JavaCompiler`)로 소스를 컴파일해 오류가 난 클래스를 본다 |

앞의 둘과 마지막 것은 이 설계서를 쓰면서 자바 21로 직접 돌려 확인했다 (10000, 10000, 9700, `GoldDiscount is not abstract and does not override abstract method name()`). 파일을 비교하는 테스트는 아직 만들지 않았다. 기준 레슨을 쓸 때(진행 순서 5번) 만든다.

테스트로 확인할 수 없는 것은 원칙의 정의 둘이다. 원칙을 만든 사람의 글을 근거로 단다.

## 출처

- 단일 책임 원칙의 정의, 바뀌는 이유를 사람으로 센다는 것: Robert C. Martin, "The Single Responsibility Principle" (2014) https://blog.cleancoder.com/uncle-bob/2014/05/08/SingleReponsibilityPrinciple.html
- 개방 폐쇄 원칙의 정의: Robert C. Martin, "The Open Closed Principle" (2014) https://blog.cleancoder.com/uncle-bob/2014/05/12/TheOpenClosedPrinciple.html
- 추상 메서드를 구현하지 않은 클래스의 컴파일 오류: 자바 언어 명세 8.1.1.1 https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.1.1.1

세 주소가 열리고 인용한 내용이 그 글에 있는 것을 2026-11-23에 확인했다.
