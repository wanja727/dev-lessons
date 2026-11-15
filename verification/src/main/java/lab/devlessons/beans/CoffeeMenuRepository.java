package lab.devlessons.beans;

import java.util.Map;
import org.springframework.stereotype.Repository;

/** 메뉴 가격 저장소. 이 레슨에서는 DB 대신 메모리의 Map을 쓴다 */
@Repository
public class CoffeeMenuRepository {

    private final Map<String, Integer> prices = Map.of("americano", 4500, "latte", 5000);

    public CoffeeMenuRepository() {
        CreationLog.add("CoffeeMenuRepository 생성");
    }

    public int priceOf(String menu) {
        Integer price = prices.get(menu);
        if (price == null) {
            throw new IllegalArgumentException("없는 메뉴: " + menu);
        }
        return price;
    }
}
