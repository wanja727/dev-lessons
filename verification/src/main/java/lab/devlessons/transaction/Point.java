package lab.devlessons.transaction;

import jakarta.persistence.*;

@Entity
@Table(name = "point_balance")
public class Point {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long userId;

    private int amount;

    protected Point() {
    }

    public Point(Long userId, int amount) {
        this.userId = userId;
        this.amount = amount;
    }

    public void add(int value) {
        this.amount += value;
    }

    public int getAmount() {
        return amount;
    }
}
