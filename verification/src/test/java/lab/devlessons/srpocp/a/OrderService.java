package lab.devlessons.srpocp.a;

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
