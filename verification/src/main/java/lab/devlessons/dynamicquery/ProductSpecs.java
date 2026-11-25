package lab.devlessons.dynamicquery;

import org.springframework.data.jpa.domain.Specification;

/** 조건 하나에 Specification 하나. 값이 null이면 null을 돌려줘 조건에서 빠진다 */
public final class ProductSpecs {

    private ProductSpecs() {
    }

    public static Specification<DqProduct> nameIs(String name) {
        return (root, q, cb) -> name == null ? null : cb.equal(root.get("name"), name);
    }

    public static Specification<DqProduct> priceAtLeast(Integer min) {
        return (root, q, cb) -> min == null ? null : cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<DqProduct> categoryIs(String category) {
        return (root, q, cb) -> category == null ? null : cb.equal(root.get("category"), category);
    }
}
