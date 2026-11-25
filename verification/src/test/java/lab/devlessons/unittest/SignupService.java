package lab.devlessons.unittest;

/** 고치기 전: existsByEmail로 중복을 본다 */
public class SignupService {
    private final MemberRepository repo;
    private final MailSender mail;

    public SignupService(MemberRepository repo, MailSender mail) {
        this.repo = repo;
        this.mail = mail;
    }

    public Member signup(String email) {
        if (repo.existsByEmail(email)) {
            throw new DuplicateEmail(email);
        }
        Member m = repo.save(new Member(email));
        mail.send(email, "가입을 환영해요");
        return m;
    }
}
