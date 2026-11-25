package lab.devlessons.inheritance;

import jakarta.persistence.Embeddable;

/** 불변 값 타입. Hibernate 6.2부터 record를 @Embeddable로 쓸 수 있다 */
@Embeddable
public record Address(String city, String street, String zipcode) {
}
