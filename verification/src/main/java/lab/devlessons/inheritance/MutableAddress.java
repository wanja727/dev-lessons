package lab.devlessons.inheritance;

import jakarta.persistence.Embeddable;

/** 흔한 실수: 바꿀 수 있는 값 타입 */
@Embeddable
public class MutableAddress {

    private String city;

    private String street;

    protected MutableAddress() {
    }

    public MutableAddress(String city, String street) {
        this.city = city;
        this.street = street;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCity() {
        return city;
    }
}
