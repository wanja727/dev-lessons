package lab.devlessons.beans;

/** 알림 보내기. 서비스는 이 인터페이스만 알고, 실제 구현은 컨테이너가 넣어 준다 */
public interface NoticeSender {

    String send(String to, String message);
}
