package lab.devlessons.springbeans.shop;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.stereotype.Service;

/**
 * 스캔 범위 안(lab.devlessons.springbeans.shop)의 빈.
 * 레슨의 SpringBeansLessonTest가 이 패키지만 직접 스캔한다.
 * 저장소 전체를 스캔하는 @SpringBootTest에는 들어가지 않게 @TestComponent를 함께 붙였다
 * (다른 레슨의 OrderService 빈과 이름이 겹친다).
 */
@Service
@TestComponent
public class OrderService {
}
