package lab.devlessons.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 해결 2: 포인트 적립을 다른 빈으로 분리해 프록시를 거치게 한다. */
@Service
public class PointService {

    private final PointRepository pointRepository;

    public PointService(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    @Transactional
    public void addPoint(Long userId, int amount) {
        Point point = pointRepository.findByUserId(userId);
        point.add(amount);
    }
}
