package az.iktex.organization.structure.testders.enums;

import lombok.Getter;

@Getter
public enum Gender {
    MALE(1),
    FEMALE(2);

    private final int id;

    Gender(int id) {
        this.id = id;
    }

    public static Gender fromId(int id) {
        for (Gender gender : values()) {
            if (gender.getId() == id) {
                return gender;
            }
        }

        throw new IllegalArgumentException("Unknown gender id: " + id);
    }
}
