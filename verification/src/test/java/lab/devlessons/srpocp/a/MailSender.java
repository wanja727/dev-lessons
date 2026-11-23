package lab.devlessons.srpocp.a;

/** 메일 서버에 접속해서 보내는 코드가 드는 곳. 테스트는 보낸 것을 적어 두는 가짜를 넣는다 */
interface MailSender {
    void send(String to, String text);
}
