package lab.devlessons.beans;

import org.springframework.stereotype.Service;

/** 주문 처리. 필요한 객체를 직접 new 하지 않고 생성자로 받는다 (생성자 주입) */
@Service
public class CoffeeService {

    private final CoffeeMenuRepository menu;
    private final NoticeSender sender;
    private int orderCount;   // 싱글톤 빈의 필드: 모든 요청이 함께 쓴다 (나쁜 예)

    public CoffeeService(CoffeeMenuRepository menu, NoticeSender sender) {
        this.menu = menu;
        this.sender = sender;
        CreationLog.add("CoffeeService 생성 (CoffeeMenuRepository, NoticeSender를 받음)");
    }

    public String order(String user, String coffee) {
        orderCount++;
        int price = menu.priceOf(coffee);
        return sender.send(user, coffee + " " + price + "원 주문 완료");
    }

    public int orderCount() {
        return orderCount;
    }

    public NoticeSender sender() {
        return sender;
    }

    public CoffeeMenuRepository menu() {
        return menu;
    }
}
