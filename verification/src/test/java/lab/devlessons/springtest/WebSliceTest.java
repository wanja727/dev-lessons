package lab.devlessons.springtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lab.devlessons.springtest.FullContextTest.MemberService;
import lab.devlessons.springtest.FullContextTest.NoMember;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 레슨 "스프링 테스트"(T3) 질문 2: @WebMvcTest는 웹 계층만 띄우고 서비스는 목으로 넣는다 */
@WebMvcTest(FullContextTest.MemberController.class)
@ContextConfiguration(classes = FullContextTest.WebApp.class)
class WebSliceTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext ctx;
    @MockitoBean MemberService service;

    @Test
    @DisplayName("@WebMvcTest에는 서비스와 리포지토리가 없고, 목 서비스가 던진 예외가 @ControllerAdvice를 거쳐 404가 된다")
    void webSliceUsesMockService() throws Exception {
        given(service.find(9L)).willThrow(new NoMember(9L));

        MvcResult result = mvc.perform(get("/members/9"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("없는 회원: 9"))
                .andReturn();

        assertThat(ctx.getBeanNamesForType(StMemberRepository.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(javax.sql.DataSource.class)).isEmpty();
        assertThat(ctx.getBeanDefinitionCount()).isLessThan(200);
        CiOutput.record(FullContextTest.LESSON, "web-beans", FullContextTest.summary("@WebMvcTest", ctx));
        CiOutput.record(FullContextTest.LESSON, "web-404", """
                given(service.find(9L)).willThrow(new NoMember(9L))
                GET /members/9
                → %d %s""".formatted(result.getResponse().getStatus(),
                result.getResponse().getContentAsString()));
    }
}
