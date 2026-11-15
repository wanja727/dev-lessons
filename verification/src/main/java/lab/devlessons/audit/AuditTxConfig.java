package lab.devlessons.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 트랜잭션 어드바이스의 순서를 0(가장 바깥)으로 정한다.
 * 그래야 순서 10인 감사 애스펙트가 트랜잭션 안쪽에서 실행된다. 정하지 않으면 둘 다 기본값(가장 낮은 우선순위)이라 순서가 보장되지 않는다.
 */
@Configuration
@EnableTransactionManagement(order = 0)
public class AuditTxConfig {
}
