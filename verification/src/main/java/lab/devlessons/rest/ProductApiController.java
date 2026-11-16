package lab.devlessons.rest;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 레슨 "HTTP와 REST API 설계": 상품 API. 저장은 메모리에 한다 (HTTP 동작에 집중).
 * 목록은 최신순(id 큰 것부터).
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductApiController {

    public record ProductRequest(String name, Long price) {
    }

    public record Product(long id, String name, long price) {
    }

    public record Page(List<Product> items, Long nextCursor) {
    }

    public static class ProductNotFoundException extends RuntimeException {
        final long id;

        ProductNotFoundException(long id) {
            super("상품 " + id + "번이 없습니다");
            this.id = id;
        }
    }

    public static class InvalidProductException extends RuntimeException {
        final Map<String, String> errors;

        InvalidProductException(Map<String, String> errors) {
            super("요청 값이 올바르지 않습니다");
            this.errors = errors;
        }
    }

    private final ConcurrentSkipListMap<Long, Product> store = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    private final AtomicLong ids = new AtomicLong();

    public void reset() {
        store.clear();
        ids.set(0);
    }

    public int count() {
        return store.size();
    }

    private static void validate(ProductRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new InvalidProductException(Map.of("name", "이름은 비어 있을 수 없습니다"));
        }
        if (req.price() == null || req.price() < 0) {
            throw new InvalidProductException(Map.of("price", "가격은 0 이상이어야 합니다"));
        }
    }

    /** POST: 새 상품을 만든다. 보낼 때마다 새로 생긴다 (멱등 아님) */
    @PostMapping
    public ResponseEntity<Product> create(@RequestBody ProductRequest req) {
        validate(req);
        long id = ids.incrementAndGet();
        Product p = new Product(id, req.name(), req.price());
        store.put(id, p);
        return ResponseEntity.created(URI.create("/api/v1/products/" + id)).body(p);
    }

    /** PUT: 이 id의 상품을 통째로 이 내용으로 둔다. 여러 번 보내도 결과가 같다 (멱등) */
    @PutMapping("/{id}")
    public ResponseEntity<Product> put(@PathVariable long id, @RequestBody ProductRequest req) {
        validate(req);
        boolean existed = store.containsKey(id);
        Product p = new Product(id, req.name(), req.price());
        store.put(id, p);
        ids.accumulateAndGet(id, Math::max);
        return existed ? ResponseEntity.ok(p) : ResponseEntity.created(URI.create("/api/v1/products/" + id)).body(p);
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable long id) {
        Product p = store.get(id);
        if (p == null) {
            throw new ProductNotFoundException(id);
        }
        return p;
    }

    /** DELETE: 지운다. 두 번째 DELETE는 404지만 서버 상태(상품 없음)는 같다 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (store.remove(id) == null) {
            throw new ProductNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    /** offset 페이지네이션: 앞에서 page*size개를 건너뛴다 */
    @GetMapping(params = "page")
    public Page byOffset(@RequestParam int page, @RequestParam(defaultValue = "3") int size) {
        List<Product> items = store.values().stream().skip((long) page * size).limit(size).toList();
        return new Page(items, null);
    }

    /** cursor 페이지네이션: 마지막으로 본 id보다 작은 것부터 */
    @GetMapping(params = "cursor")
    public Page byCursor(@RequestParam long cursor, @RequestParam(defaultValue = "3") int size) {
        List<Product> items = store.tailMap(cursor, false).values().stream().limit(size).toList();
        Long next = items.isEmpty() ? null : items.get(items.size() - 1).id();
        return new Page(items, next);
    }

    /** 첫 페이지 (cursor 방식의 시작) */
    @GetMapping
    public Page first(@RequestParam(defaultValue = "3") int size) {
        return byCursor(Long.MAX_VALUE, size);
    }

    /** 이 컨트롤러의 오류만 RFC 9457 Problem Details 형식으로 바꾼다 */
    @RestControllerAdvice(assignableTypes = ProductApiController.class)
    public static class ProductApiErrors {

        @ExceptionHandler
        public ProblemDetail notFound(ProductNotFoundException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
            pd.setTitle("상품 없음");
            pd.setType(URI.create("https://example.com/problems/product-not-found"));
            pd.setProperty("productId", e.id);
            return pd;
        }

        @ExceptionHandler
        public ProblemDetail invalid(InvalidProductException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            pd.setTitle("잘못된 요청");
            pd.setProperty("errors", e.errors);
            return pd;
        }
    }
}
