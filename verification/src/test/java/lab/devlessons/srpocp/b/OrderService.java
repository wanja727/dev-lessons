package lab.devlessons.srpocp.b;

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
