package lab.devlessons.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.http.HttpRequest;
import java.util.Arrays;
import java.util.Map;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "빌더 패턴"(builder)의 주장을 확인한다.
 */
class BuilderTest {

    private static final String LESSON = "builder";

    // ── 코드 ctor ──
    static class CtorProduct {
        final String name;
        final int price;
        final int stock;

        CtorProduct(String name, int price, int stock, String category, int discountRate, String description,
                boolean visible) {
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
    }

    @Test
    @DisplayName("코드 ctor: 가격과 재고를 바꿔 넣어도 컴파일되고 가격 100, 재고 25000이 된다")
    void swappedArguments() {
        CtorProduct p = new CtorProduct("머그컵", 100, 25000, "주방", 0, null, true);

        assertThat(p.price).isEqualTo(100);
        assertThat(p.stock).isEqualTo(25000);
        CiOutput.record(LESSON, "ctor", "new Product(\"머그컵\", 100, 25000, \"주방\", 0, null, true)\n→ 가격 "
                + p.price + "원, 재고 " + p.stock + "개");
    }

    // ── 코드 beans ──
    static class BeanProduct {
        private String name;
        private int price;
        private int stock;

        void setName(String name) {
            this.name = name;
        }

        void setPrice(int price) {
            this.price = price;
        }

        void setStock(int stock) {
            this.stock = stock;
        }
    }

    static class FakeRepository {
        String saved;

        void save(BeanProduct p) {
            saved = "이름 " + p.name + ", 가격 " + p.price + ", 재고 " + p.stock;
        }
    }

    @Test
    @DisplayName("코드 beans: 재고를 넣기 전에 저장하면 저장 시점의 재고는 0이다")
    void incompleteState() {
        FakeRepository repository = new FakeRepository();
        BeanProduct p = new BeanProduct();
        p.setName("머그컵");
        p.setPrice(25000);
        repository.save(p);
        p.setStock(100);

        assertThat(repository.saved).isEqualTo("이름 머그컵, 가격 25000, 재고 0");
        CiOutput.record(LESSON, "beans", "save(p) 시점에 저장된 값\n→ " + repository.saved);
    }

    // ── 코드 builder ──
    static final class Product {
        private final String name;
        private final int price;
        private final int stock;

        private Product(Builder b) {
            this.name = b.name;
            this.price = b.price;
            this.stock = b.stock;
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private String name;
            private int price;
            private int stock;

            Builder name(String v) {
                name = v;
                return this;
            }

            Builder price(int v) {
                price = v;
                return this;
            }

            Builder stock(int v) {
                stock = v;
                return this;
            }

            Product build() {
                if (name == null) {
                    throw new IllegalStateException("name은 꼭 있어야 해요");
                }
                return new Product(this);
            }
        }
    }

    @Test
    @DisplayName("빌더: 메서드 순서와 상관없이 같은 결과, 이름 없이 build하면 IllegalStateException, 필드는 모두 final")
    void builder() {
        Product a = Product.builder().name("머그컵").price(25000).stock(100).build();
        Product b = Product.builder().stock(100).price(25000).name("머그컵").build();
        boolean allFinal = Arrays.stream(Product.class.getDeclaredFields())
                .allMatch(f -> Modifier.isFinal(f.getModifiers()));

        assertThat(a.price).isEqualTo(b.price).isEqualTo(25000);
        assertThat(a.stock).isEqualTo(b.stock).isEqualTo(100);
        assertThatThrownBy(() -> Product.builder().price(25000).build())
                .isInstanceOf(IllegalStateException.class).hasMessage("name은 꼭 있어야 해요");
        assertThat(allFinal).isTrue();
        CiOutput.record(LESSON, "builder", ".name(\"머그컵\").price(25000).stock(100) → 가격 " + a.price + ", 재고 " + a.stock
                + "\n.stock(100).price(25000).name(\"머그컵\") → 가격 " + b.price + ", 재고 " + b.stock
                + "\n\nProduct.builder().price(25000).build()\n→ IllegalStateException: name은 꼭 있어야 해요"
                + "\n\nProduct의 필드가 모두 final인가 → " + allFinal);
    }

    @Test
    @DisplayName("필수값을 builder(name, price)로 받으면 빠뜨렸을 때 컴파일 오류, 메서드로 받으면 컴파일된다")
    void requiredInBuilderArguments() {
        String product = """
                final class Product {
                  private final String name; private final int price; private final int stock;
                  private Product(Builder b) { name = b.name; price = b.price; stock = b.stock; }
                  static Builder builder(String name, int price) { return new Builder(name, price); }
                  static final class Builder {
                    private final String name; private final int price; private int stock;
                    Builder(String name, int price) { this.name = name; this.price = price; }
                    Builder stock(int v) { stock = v; return this; }
                    Product build() { return new Product(this); }
                  }
                }
                """;
        BrokenFiles missing = BrokenFiles.compile(Map.of("Product", product, "Shop", """
                class Shop { Product p = Product.builder().stock(100).build(); }
                """));
        BrokenFiles ok = BrokenFiles.compile(Map.of("Product", product, "Shop", """
                class Shop { Product p = Product.builder("머그컵", 25000).stock(100).build(); }
                """));

        assertThat(missing.files()).containsExactly("Shop");
        assertThat(ok.ok()).isTrue();
        CiOutput.record(LESSON, "required", "Product.builder().stock(100).build()\n→ 컴파일 오류\nerror: "
                + missing.messages().get(0).substring("Shop: ".length())
                + "\n\nProduct.builder(\"머그컵\", 25000).stock(100).build()\n→ 컴파일 성공");
    }

    @Test
    @DisplayName("HttpRequest.newBuilder().build()는 URI가 없어서 IllegalStateException")
    void httpRequestBuilder() {
        assertThatThrownBy(() -> HttpRequest.newBuilder().build()).isInstanceOf(IllegalStateException.class);
        String msg;
        try {
            HttpRequest.newBuilder().build();
            msg = "성공";
        } catch (IllegalStateException e) {
            msg = "IllegalStateException: " + e.getMessage();
        }
        CiOutput.record(LESSON, "http", "HttpRequest.newBuilder().build()\n→ " + msg);
    }

    @Test
    @DisplayName("Product의 필드 이름을 확인한다 (리플렉션이 본 필드가 셋)")
    void fieldsAreThree() {
        assertThat(Arrays.stream(Product.class.getDeclaredFields()).map(Field::getName))
                .containsExactlyInAnyOrder("name", "price", "stock");
    }
}
