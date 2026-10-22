package az.iktex.organization.structure.testders;

import az.iktex.organization.structure.testders.entity.*;
import az.iktex.organization.structure.testders.entity.converter.GenderConverter;
import az.iktex.organization.structure.testders.enums.Gender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EntityRelationsTests {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void genderConverterHandlesIdsAndNull() {
        GenderConverter converter = new GenderConverter();
        assertEquals(1, converter.convertToDatabaseColumn(Gender.MALE));
        assertEquals(2, converter.convertToDatabaseColumn(Gender.FEMALE));
        assertEquals(Gender.MALE, converter.convertToEntityAttribute(1));
        assertEquals(Gender.FEMALE, converter.convertToEntityAttribute(2));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToEntityAttribute(99));
    }

    @Test
    void userSavesPassportAndGender() {
        User user = userWithPassport();
        assertSame(user, user.getPassport().getUser());
        entityManager.persist(user);
        flushAndClear();

        User saved = entityManager.find(User.class, user.getId());
        assertEquals(Gender.MALE, saved.getGender());
        assertEquals("AA1234567", saved.getPassport().getSerialNumber());
        Number genderId = (Number) entityManager.createNativeQuery(
                "select gender_id from users where id = :id")
                .setParameter("id", user.getId()).getSingleResult();
        assertEquals(1, genderId.intValue());
    }

    @Test
    void deletingUserDeletesPassport() {
        User user = userWithPassport();
        entityManager.persist(user);
        Long passportId = user.getPassport().getId();
        flushAndClear();
        entityManager.remove(entityManager.find(User.class, user.getId()));
        flushAndClear();

        assertNull(entityManager.find(User.class, user.getId()));
        assertNull(entityManager.find(Passport.class, passportId));
    }

    @Test
    void removingPassportFromUserDeletesOrphan() {
        User user = userWithPassport();
        entityManager.persist(user);
        Long passportId = user.getPassport().getId();
        flushAndClear();
        User saved = entityManager.find(User.class, user.getId());
        Passport passport = saved.getPassport();
        saved.setPassport(null);
        assertNull(passport.getUser());
        flushAndClear();

        assertNotNull(entityManager.find(User.class, user.getId()));
        assertNull(entityManager.find(Passport.class, passportId));
    }

    @Test
    void deletingPassportKeepsUser() {
        User user = userWithPassport();
        entityManager.persist(user);
        Long passportId = user.getPassport().getId();
        flushAndClear();
        entityManager.remove(entityManager.find(Passport.class, passportId));
        flushAndClear();

        assertNull(entityManager.find(Passport.class, passportId));
        assertNotNull(entityManager.find(User.class, user.getId()));
    }

    @Test
    void deletingDepartmentDeletesEmployees() {
        Department department = departmentWithEmployee();
        Employee second = employee("Ana");
        department.addEmployee(second);
        entityManager.persist(department);
        Long firstId = department.getEmployees().getFirst().getId();
        Long secondId = second.getId();
        flushAndClear();
        entityManager.remove(entityManager.find(Department.class, department.getId()));
        flushAndClear();

        assertNull(entityManager.find(Department.class, department.getId()));
        assertNull(entityManager.find(Employee.class, firstId));
        assertNull(entityManager.find(Employee.class, secondId));
    }

    @Test
    void removingEmployeeDeletesOrphan() {
        Department department = departmentWithEmployee();
        entityManager.persist(department);
        Long employeeId = department.getEmployees().getFirst().getId();
        flushAndClear();
        Department saved = entityManager.find(Department.class, department.getId());
        Employee employee = saved.getEmployees().getFirst();
        saved.removeEmployee(employee);
        assertNull(employee.getDepartment());
        flushAndClear();

        assertNull(entityManager.find(Employee.class, employeeId));
        assertNotNull(entityManager.find(Department.class, department.getId()));
    }

    @Test
    void deletingEmployeeKeepsDepartment() {
        Department department = departmentWithEmployee();
        entityManager.persist(department);
        Long employeeId = department.getEmployees().getFirst().getId();
        flushAndClear();
        entityManager.remove(entityManager.find(Employee.class, employeeId));
        flushAndClear();

        assertNull(entityManager.find(Employee.class, employeeId));
        assertNotNull(entityManager.find(Department.class, department.getId()));
    }

    @Test
    void deletingStudentKeepsCourseAndRemovesJoinRow() {
        Student student = studentWithCourse();
        Course course = student.getCourses().iterator().next();
        entityManager.persist(student);
        flushAndClear();
        entityManager.remove(entityManager.find(Student.class, student.getId()));
        flushAndClear();

        assertNull(entityManager.find(Student.class, student.getId()));
        assertNotNull(entityManager.find(Course.class, course.getId()));
        Number count = (Number) entityManager.createNativeQuery(
                "select count(*) from student_course where student_id = :id")
                .setParameter("id", student.getId()).getSingleResult();
        assertEquals(0, count.intValue());
    }

    @Test
    void removingCourseOnlyDeletesJoinRow() {
        Student student = studentWithCourse();
        Course course = student.getCourses().iterator().next();
        assertTrue(course.getStudents().contains(student));
        entityManager.persist(student);
        flushAndClear();
        Student saved = entityManager.find(Student.class, student.getId());
        Course savedCourse = saved.getCourses().iterator().next();
        saved.removeCourse(savedCourse);
        assertFalse(savedCourse.getStudents().contains(saved));
        flushAndClear();

        assertTrue(entityManager.find(Student.class, student.getId()).getCourses().isEmpty());
        assertNotNull(entityManager.find(Course.class, course.getId()));
    }

    @Test
    void deletingAuthorKeepsBook() {
        Author author = authorWithBook();
        Long bookId = author.getAuthorBooks().getFirst().getBook().getId();
        flushAndClear();
        entityManager.remove(entityManager.find(Author.class, author.getId()));
        flushAndClear();

        assertNull(entityManager.find(Author.class, author.getId()));
        assertNotNull(entityManager.find(Book.class, bookId));
        assertEquals(0L, linkCount());
    }

    @Test
    void deletingBookKeepsAuthor() {
        Author author = authorWithBook();
        Long bookId = author.getAuthorBooks().getFirst().getBook().getId();
        flushAndClear();
        entityManager.remove(entityManager.find(Book.class, bookId));
        flushAndClear();

        assertNull(entityManager.find(Book.class, bookId));
        assertNotNull(entityManager.find(Author.class, author.getId()));
        assertEquals(0L, linkCount());
    }

    @Test
    void deletingAuthorBookKeepsBothParents() {
        Author author = authorWithBook();
        AuthorBook link = author.getAuthorBooks().getFirst();
        Long linkId = link.getId();
        Long bookId = link.getBook().getId();
        flushAndClear();
        entityManager.remove(entityManager.find(AuthorBook.class, linkId));
        flushAndClear();

        assertNull(entityManager.find(AuthorBook.class, linkId));
        assertNotNull(entityManager.find(Author.class, author.getId()));
        assertNotNull(entityManager.find(Book.class, bookId));
    }

    @Test
    void removingAuthorBookSynchronizesBothSides() {
        Author author = authorWithBook();
        Long bookId = author.getAuthorBooks().getFirst().getBook().getId();
        flushAndClear();
        Author saved = entityManager.find(Author.class, author.getId());
        AuthorBook link = saved.getAuthorBooks().getFirst();
        Book book = link.getBook();
        saved.removeAuthorBook(link);
        assertTrue(book.getAuthorBooks().isEmpty());
        assertNull(link.getAuthor());
        assertNull(link.getBook());
        flushAndClear();

        assertEquals(0L, linkCount());
        assertNotNull(entityManager.find(Author.class, author.getId()));
        assertNotNull(entityManager.find(Book.class, bookId));
    }

    private User userWithPassport() {
        User user = new User();
        user.setName("Vaqif");
        user.setSurname("Pashayev");
        user.setEmail("vaqif@example.com");
        user.setGender(Gender.MALE);
        Passport passport = new Passport();
        passport.setSerialNumber("AA1234567");
        passport.setIssueDate(LocalDate.of(2026, 1, 1));
        passport.setCountry("Azerbaijan");
        user.setPassport(passport);
        return user;
    }

    private Employee employee(String name) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setSurname("Aliyev");
        employee.setSalary(new BigDecimal("1500.00"));
        return employee;
    }

    private Department departmentWithEmployee() {
        Department department = new Department();
        department.setName("IT");
        department.addEmployee(employee("Vaqif"));
        return department;
    }

    private Student studentWithCourse() {
        Student student = new Student();
        student.setName("Vaqif");
        student.setSurname("Pashayev");
        student.setAge(23);
        Course course = new Course();
        course.setTitle("Java");
        course.setCredit(5);
        course.setTeacher("Ruslan");
        student.addCourse(course);
        return student;
    }

    private Author authorWithBook() {
        Author author = new Author();
        author.setName("Ali");
        author.setSurname("Aliyev");
        Book book = new Book();
        book.setTitle("Java");
        book.setPrice(new BigDecimal("25.50"));
        book.setPageCount(200);
        entityManager.persist(book);
        author.addBook(book);
        entityManager.persist(author);
        return author;
    }

    private long linkCount() {
        return entityManager.createQuery("select count(a) from AuthorBook a", Long.class)
                .getSingleResult();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
