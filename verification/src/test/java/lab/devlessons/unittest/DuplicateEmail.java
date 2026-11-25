package lab.devlessons.unittest;

public class DuplicateEmail extends RuntimeException {
    public DuplicateEmail(String email) {
        super("이미 가입한 이메일: " + email);
    }
}
