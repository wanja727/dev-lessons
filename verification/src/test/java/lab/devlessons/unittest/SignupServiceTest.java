package lab.devlessons.unittest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 레슨 "단위 테스트와 테스트 더블"(T2)의 실무 구역: 프로젝트에 그대로 들어갈 모양의 테스트 */
@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock MemberRepository repo;
    @Mock MailSender mail;
    @InjectMocks SignupService service;

    @Nested
    @DisplayName("이미 있는 이메일이면")
    class Duplicate {
        @Test
        void 예외를_던지고_메일은_없다() {
            given(repo.existsByEmail(any())).willReturn(true);

            assertThatThrownBy(() -> service.signup("a@b.com"))
                    .isInstanceOf(DuplicateEmail.class);
            then(mail).should(never()).send(any(), any());
        }
    }

    @Nested
    @DisplayName("새 이메일이면")
    class NewEmail {
        @Test
        void 저장하고_환영_메일을_보낸다() {
            given(repo.save(any())).willAnswer(inv -> inv.getArgument(0));

            Member m = service.signup("a@b.com");

            assertThat(m.email()).isEqualTo("a@b.com");
            then(mail).should().send("a@b.com", "가입을 환영해요");
        }
    }

    @ParameterizedTest(name = "{0}원이면 배송비 {1}원")
    @CsvSource({"29999, 3000", "30000, 0", "50000, 0"})
    void 배송비_경계값(int price, int fee) {
        assertThat(ShippingFee.fee(price)).isEqualTo(fee);
    }
}
