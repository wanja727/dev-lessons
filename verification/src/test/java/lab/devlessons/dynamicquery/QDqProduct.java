package lab.devlessons.dynamicquery;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;

/**
 * querydsl-apt가 DqProduct에서 만들어 낼 Q 클래스와 같은 모양이다.
 * 이 저장소는 빌드에 코드 생성을 넣지 않으려고 손으로 둔다. 실무에서는 빌드가 만든다.
 */
public class QDqProduct extends EntityPathBase<DqProduct> {

    public static final QDqProduct dqProduct = new QDqProduct("dqProduct");

    public final NumberPath<Long> id = createNumber("id", Long.class);
    public final StringPath name = createString("name");
    public final NumberPath<Integer> price = createNumber("price", Integer.class);
    public final StringPath category = createString("category");

    public QDqProduct(String variable) {
        super(DqProduct.class, forVariable(variable));
    }
}
