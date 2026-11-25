package lab.devlessons.exceptionvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 레슨 "예외 처리와 검증"(S8)의 주장을 작은 웹 앱(MiniWeb)으로 확인한다. 검증은 spring-boot-starter-validation(Hibernate Validator)을 쓴다. */
class ExceptionValidationTest {

    private static final String LESSON = "exception-validation";

    public enum Grade { BASIC, VIP }

    public record Member(long id, String email) {
    }

    /** 레슨의 코드 request */
    public record SignUp(@NotBlank @Email String email, @NotNull @Min(0) Integer age, Grade grade) {
    }

    public static class MemberNotFoundException extends RuntimeException {
        public MemberNotFoundException(long id) {
            super("회원 " + id + "를 찾을 수 없어요");
        }
    }

    static final AtomicInteger CREATE_CALLS = new AtomicInteger();

    @RestController
    static class MemberController {
        @GetMapping("/members/{id}")
        Member find(@PathVariable long id) {
            if (id == 1) {
                return new Member(1, "kim@a.com");
            }
            throw new MemberNotFoundException(id);
        }

        @PostMapping("/members")
        Member create(@RequestBody @Valid SignUp req) {
            CREATE_CALLS.incrementAndGet();
            return new Member(2, req.email());
        }

        @PostMapping("/members/no-valid")
        SignUp createWithoutValid(@RequestBody SignUp req) {
            return req;
        }

        @GetMapping("/members")
        List<Member> page(@RequestParam @Min(1) int page) {
            return List.of();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("DB 연결 실패: select * from member");
        }
    }

    /** 레슨의 코드 advice + invalid + 본문을 읽지 못함 + fallback */
    @RestControllerAdvice
    static class ApiExceptionHandler {
        @ExceptionHandler(MemberNotFoundException.class)
        ProblemDetail notFound(MemberNotFoundException e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        }

        @ExceptionHandler(Exception.class)
        ProblemDetail unknown(Exception e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요");
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        ProblemDetail invalid(MethodArgumentNotValidException e) {
            ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력 값이 올바르지 않아요");
            p.setProperty("errors", e.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().toList());
            return p;
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        ProblemDetail unreadable(HttpMessageNotReadableException e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없어요. 값의 형식을 확인해 주세요");
        }
    }

    /** 컨트롤러 안의 핸들러가 어드바이스보다 먼저인지 보는 컨트롤러 */
    @RestController
    static class OrderController {
        @GetMapping("/orders/conflict")
        String conflict() {
            throw new IllegalStateException("이미 결제된 주문");
        }

        @ExceptionHandler(IllegalStateException.class)
        ProblemDetail local(IllegalStateException e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** 실무 구역: ResponseEntityExceptionHandler를 상속한 어드바이스 */
    @RestControllerAdvice
    static class StandardAdvice extends ResponseEntityExceptionHandler {
    }

    private static HttpResponse<String> post(MiniWeb web, String path, String json) {
        return web.exchange(web.request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private static HttpResponse<String> get(MiniWeb web, String path) {
        return web.exchange(web.request(path).GET());
    }

    private static String clean(String body) {
        return body.replaceAll("\"timestamp\":\"[^\"]*\",", "").replaceAll("\"timestamp\":\\d+,", "");
    }

    @Test
    @DisplayName("처리하지 않은 내 예외는 500이고, 기본 본문은 status, error, path를 가진다. 타입 변환 실패는 400이다")
    void defaultErrorResponse() {
        try (MiniWeb web = MiniWeb.startWith(new String[] {"server.error.include-message=never"}, MemberController.class)) {
            HttpResponse<String> notFound = get(web, "/members/999");
            HttpResponse<String> typo = get(web, "/members/abc");

            assertThat(notFound.statusCode()).isEqualTo(500);
            assertThat(notFound.body()).contains("\"status\":500", "\"error\":\"Internal Server Error\"", "\"path\":\"/members/999\"")
                    .doesNotContain("찾을 수 없어요");
            assertThat(typo.statusCode()).isEqualTo(400);
            CiOutput.record(LESSON, "default", "GET /members/999 → " + notFound.statusCode() + "\n" + notFound.body()
                    + "\n\nGET /members/abc → " + typo.statusCode() + "\n" + typo.body());
        }
    }

    @Test
    @DisplayName("어드바이스의 핸들러가 404와 ProblemDetail 본문을 만들고 Content-Type은 application/problem+json이다. 더 가까운 타입의 핸들러가 골라진다")
    void adviceTurnsExceptionIntoProblem() {
        try (MiniWeb web = MiniWeb.start(MemberController.class, ApiExceptionHandler.class)) {
            HttpResponse<String> res = get(web, "/members/999");

            assertThat(res.statusCode()).isEqualTo(404);
            assertThat(res.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            assertThat(res.body()).contains("\"detail\":\"회원 999를 찾을 수 없어요\"");
            CiOutput.record(LESSON, "advice", "GET /members/999 → " + res.statusCode() + "\nContent-Type: "
                    + res.headers().firstValue("Content-Type").orElse("") + "\n" + res.body());
        }
    }

    @Test
    @DisplayName("컨트롤러 안의 @ExceptionHandler가 어드바이스보다 먼저다")
    void localHandlerFirst() {
        try (MiniWeb web = MiniWeb.start(OrderController.class, MemberController.class, ApiExceptionHandler.class)) {
            HttpResponse<String> local = get(web, "/orders/conflict");
            HttpResponse<String> other = get(web, "/boom");

            assertThat(local.statusCode()).isEqualTo(409);
            assertThat(other.statusCode()).isEqualTo(500);
            CiOutput.record(LESSON, "local-first", "GET /orders/conflict (컨트롤러 안에 핸들러) → " + local.statusCode()
                    + "\nGET /boom (어드바이스의 Exception 핸들러) → " + other.statusCode() + "\n" + other.body());
        }
    }

    @Test
    @DisplayName("@Valid 요청에 잘못된 값을 보내면 컨트롤러를 부르지 않고 400과 필드 오류를 얻는다. @Valid가 없으면 200")
    void validation() {
        try (MiniWeb web = MiniWeb.start(MemberController.class, ApiExceptionHandler.class)) {
            int before = CREATE_CALLS.get();
            String bad = "{\"email\":\" \",\"age\":-1}";
            HttpResponse<String> invalid = post(web, "/members", bad);
            HttpResponse<String> noValid = post(web, "/members/no-valid", bad);

            assertThat(invalid.statusCode()).isEqualTo(400);
            assertThat(CREATE_CALLS.get()).isEqualTo(before);
            assertThat(invalid.body()).contains("email").contains("age");
            assertThat(noValid.statusCode()).isEqualTo(200);
            CiOutput.record(LESSON, "valid", "보낸 본문: " + bad + "\n\nPOST /members (@Valid) → " + invalid.statusCode()
                    + "\n컨트롤러 실행 횟수: " + (CREATE_CALLS.get() - before) + "\n" + invalid.body()
                    + "\n\nPOST /members/no-valid (@Valid 없음) → " + noValid.statusCode() + "\n" + noValid.body());
        }
    }

    record NotNullBox(@NotNull String v) {
    }

    record NotEmptyBox(@NotEmpty String v) {
    }

    record NotBlankBox(@NotBlank String v) {
    }

    record PrimitiveAge(@NotNull int age) {
    }

    @Test
    @DisplayName("null, 빈 문자열, 공백 문자열에 대한 @NotNull, @NotEmpty, @NotBlank의 결과. int 필드의 @NotNull은 걸리지 않는다")
    void notNullEmptyBlank() {
        Validator v = Validation.buildDefaultValidatorFactory().getValidator();
        String[] values = {null, "", " "};
        String[] shown = {"null", "\"\"", "\" \""};
        List<String> rows = new ArrayList<>();
        rows.add("값       @NotNull  @NotEmpty  @NotBlank");
        boolean[][] blocked = new boolean[3][3];
        for (int i = 0; i < 3; i++) {
            blocked[i][0] = !v.validate(new NotNullBox(values[i])).isEmpty();
            blocked[i][1] = !v.validate(new NotEmptyBox(values[i])).isEmpty();
            blocked[i][2] = !v.validate(new NotBlankBox(values[i])).isEmpty();
            rows.add(String.format("%-7s  %-8s  %-9s  %s", shown[i], mark(blocked[i][0]), mark(blocked[i][1]), mark(blocked[i][2])));
        }
        assertThat(blocked[0]).containsExactly(true, true, true);
        assertThat(blocked[1]).containsExactly(false, true, true);
        assertThat(blocked[2]).containsExactly(false, false, true);
        assertThat(v.validate(new PrimitiveAge(0))).isEmpty();
        CiOutput.record(LESSON, "not-null-blank", String.join("\n", rows));
    }

    private static String mark(boolean b) {
        return b ? "막음" : "통과";
    }

    @Test
    @DisplayName("@RequestParam @Min(1) int page에 0을 주면 컨트롤러 전에 400이다")
    void methodValidation() {
        try (MiniWeb web = MiniWeb.start(MemberController.class)) {
            HttpResponse<String> zero = get(web, "/members?page=0");
            HttpResponse<String> one = get(web, "/members?page=1");

            assertThat(zero.statusCode()).isEqualTo(400);
            assertThat(one.statusCode()).isEqualTo(200);
            CiOutput.record(LESSON, "method-validation", "GET /members?page=0 → " + zero.statusCode() + "\n" + clean(zero.body())
                    + "\nGET /members?page=1 → " + one.statusCode());
        }
    }

    @Test
    @DisplayName("enum에 없는 값은 HttpMessageNotReadableException으로 400이고, 핸들러로 같은 형식으로 바꿀 수 있다")
    void enumConversionFailure() {
        String body = "{\"email\":\"a@b.com\",\"age\":20,\"grade\":\"VIPP\"}";
        try (MiniWeb plain = MiniWeb.start(MemberController.class)) {
            HttpResponse<String> res = post(plain, "/members", body);
            assertThat(res.statusCode()).isEqualTo(400);
            CiOutput.record(LESSON, "enum-default", "보낸 본문: " + body + "\n어드바이스 없음 → " + res.statusCode() + "\n" + clean(res.body()));
        }
        try (MiniWeb web = MiniWeb.start(MemberController.class, ApiExceptionHandler.class)) {
            HttpResponse<String> res = post(web, "/members", body);
            assertThat(res.statusCode()).isEqualTo(400);
            assertThat(res.body()).contains("요청 본문을 읽을 수 없어요");
            CiOutput.record(LESSON, "enum-handled", "HttpMessageNotReadableException 핸들러 → " + res.statusCode() + "\n" + res.body());
        }
    }

    @Test
    @DisplayName("Exception 그물은 예상하지 못한 예외도 같은 형식의 500으로 내보내고 예외 메시지를 응답에 넣지 않는다")
    void fallback() {
        try (MiniWeb web = MiniWeb.start(MemberController.class, ApiExceptionHandler.class)) {
            HttpResponse<String> res = get(web, "/boom");

            assertThat(res.statusCode()).isEqualTo(500);
            assertThat(res.body()).contains("잠시 후 다시 시도해 주세요").doesNotContain("select");
            CiOutput.record(LESSON, "fallback", "GET /boom (예외 메시지에 SQL이 들어 있음) → " + res.statusCode() + "\n" + res.body());
        }
    }

    @Test
    @DisplayName("ResponseEntityExceptionHandler를 상속하거나 spring.mvc.problemdetails.enabled=true면 타입 변환 실패도 ProblemDetail 본문이다")
    void standardProblemDetails() {
        try (MiniWeb web = MiniWeb.start(MemberController.class, StandardAdvice.class);
                MiniWeb prop = MiniWeb.startWith(new String[] {"spring.mvc.problemdetails.enabled=true"}, MemberController.class)) {
            HttpResponse<String> a = get(web, "/members/abc");
            HttpResponse<String> b = get(prop, "/members/abc");

            assertThat(a.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            assertThat(b.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            CiOutput.record(LESSON, "standard", "ResponseEntityExceptionHandler 상속, GET /members/abc → " + a.statusCode() + "\n" + a.body()
                    + "\n\nspring.mvc.problemdetails.enabled=true, GET /members/abc → " + b.statusCode() + "\n" + b.body());
        }
    }
}
