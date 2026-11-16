package lab.devlessons.beans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 필드 주입: 컨테이너가 만든 객체에만 값이 들어간다. new로 만들면 sender는 null */
@Service
public class FieldInjectedService {

    @Autowired
    private NoticeSender sender;

    public NoticeSender sender() {
        return sender;
    }
}
