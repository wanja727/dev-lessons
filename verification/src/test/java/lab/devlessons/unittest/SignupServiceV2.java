package lab.devlessons.unittest;

/** 고친 뒤: 동작은 같고 findByEmail로 중복을 본다 */
public class SignupServiceV2 {
    private final MemberRepository repo;
    private final MailSender mail;

    public SignupServiceV2(MemberRepository repo, MailSender mail) {
        this.repo = repo;
        this.mail = mail;
    }

    public Member signup(String email) {
        if (repo.findByEmail(email).isPresent()) {
            throw new DuplicateEmail(email);
        }
        Member m = repo.save(new Member(email));
        mail.send(email, "가입을 환영해요");
        return m;
    }
}
