package lab.devlessons.persistencecontext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    /** 변경 감지: save()를 부르지 않아도 커밋 시 UPDATE */
    @Transactional
    public void rename(Long id, String title) {
        bookRepository.findById(id).orElseThrow().changeTitle(title);
    }

    /** 읽기 전용 트랜잭션: 변경 감지를 하지 않는다 */
    @Transactional(readOnly = true)
    public void renameInReadOnly(Long id, String title) {
        bookRepository.findById(id).orElseThrow().changeTitle(title);
    }

    /**
     * 내부 호출한 find()의 readOnly는 적용되지 않는다(프록시를 거치지 않음).
     * 바깥 쓰기 트랜잭션 안에서 영속 상태라 변경 감지로 반영된다. 문제 [코드 2-A] 검증용.
     */
    @Transactional
    public void renameViaFind(Long id, String title) {
        Book book = find(id);
        book.changeTitle(title);
    }

    /** 트랜잭션이 끝나면 영속성 컨텍스트도 닫혀서, 돌려준 엔티티는 준영속이 된다 */
    @Transactional(readOnly = true)
    public Book find(Long id) {
        return bookRepository.findById(id).orElseThrow();
    }
}
