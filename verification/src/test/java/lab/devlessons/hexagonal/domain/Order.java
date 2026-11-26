package lab.devlessons.hexagonal.domain;

/** 도메인: 주문. 바깥 기술(스프링, JPA, HTTP)을 모른다 */
public class Order {
    private final String id;
    private final long amount;
    private String status = "CREATED";

    public Order(String id, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 해요");
        }
        this.id = id;
        this.amount = amount;
    }

    public void markPaid() {
        status = "PAID";
    }

    public String id() {
        return id;
    }

    public long amount() {
        return amount;
    }

    public String status() {
        return status;
    }
}
