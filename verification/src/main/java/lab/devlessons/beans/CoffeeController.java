package lab.devlessons.beans;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP 요청을 받아 서비스에 넘긴다. 요청 → 컨트롤러 → 서비스 → 리포지토리 */
@RestController
public class CoffeeController {

    private final CoffeeService coffeeService;

    public CoffeeController(CoffeeService coffeeService) {
        this.coffeeService = coffeeService;
        CreationLog.add("CoffeeController 생성 (CoffeeService를 받음)");
    }

    @GetMapping("/beans/coffee")
    public String order(@RequestParam String user, @RequestParam String menu) {
        return coffeeService.order(user, menu);
    }
}
