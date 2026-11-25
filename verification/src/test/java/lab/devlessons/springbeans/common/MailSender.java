package lab.devlessons.springbeans.common;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.stereotype.Component;

/** 스캔 범위 밖(lab.devlessons.springbeans.common)에 둔 클래스. @SpringBootTest의 스캔에서는 뺀다 */
@Component
@TestComponent
public class MailSender {
}
