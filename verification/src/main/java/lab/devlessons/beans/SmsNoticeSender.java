package lab.devlessons.beans;

import org.springframework.stereotype.Component;

@Component
public class SmsNoticeSender implements NoticeSender {

    public SmsNoticeSender() {
        CreationLog.add("SmsNoticeSender 생성");
    }

    @Override
    public String send(String to, String message) {
        return "[SMS → " + to + "] " + message;
    }
}
