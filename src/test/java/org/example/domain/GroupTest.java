package org.example.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.example.domain.GroupTestHelper.calculateTotal;
import static org.junit.jupiter.api.Assertions.*;

public class GroupTest {
    private Group group;
    static List<BigDecimal> negativeAmounts = List.of(BigDecimal.valueOf(-100.0), BigDecimal.valueOf(-50.5), BigDecimal.valueOf(-0.01), BigDecimal.ZERO);

    @BeforeEach
    public void setUp() {
        group = new Group("1", "Test Group", "This is a test group.");
    }

    @Test
    public void shouldReturnDuplicateMemberMessage() {
        Member member = new Member("1", "John Doe", "");
        group.addMember(member);
        assertThrows(IllegalArgumentException.class, () -> group.addMember(member), "Member with uid 1 already exists in the group.");
    }

    @Test
    public void shouldThrowExceptionWhenAddingExpenseForNonExistingMember() {
        Member member = new Member("1", "John Doe", "");
        assertThrows(IllegalArgumentException.class, () -> group.addExpense(member.uid(), BigDecimal.valueOf(100)));
    }

    @ParameterizedTest
    @FieldSource("negativeAmounts")
    public void shouldThrowExceptionWhenAddingNegativeExpense(BigDecimal amount) {
        Member member = new Member("1", "John Doe", "");
        group.addMember(member);
        assertThrows(IllegalArgumentException.class, () -> group.addExpense(member.uid(), amount), "Amount must be non-negative.");
    }

    @Test
    public void shouldAddExpenseSuccessfullyWithTwoMembers() {
        Member member1 = new Member("1", "John Doe", "");
        Member member2 = new Member("2", "Jane Doe", "");
        group.addMember(member1);
        group.addMember(member2);
        group.addExpense("1", BigDecimal.valueOf(100.00));
        assertEquals(0, group.getExpenseForMember("1").compareTo(BigDecimal.valueOf(50.00)));
        assertEquals(0, group.getExpenseForMember("2").compareTo(BigDecimal.valueOf(-50.00)));
        assertEquals(0, calculateTotal(group).compareTo(BigDecimal.ZERO));
    }

    @Test
    public void shouldAddExpenseSuccessfullyWithThreeMembers() {
        Member member1 = new Member("1", "John Doe", "");
        Member member2 = new Member("2", "Jane Doe", "");
        Member member3 = new Member("3", "Jim Doe", "");
        group.addMember(member1);
        group.addMember(member2);
        group.addMember(member3);
        group.addExpense("1", BigDecimal.valueOf(100.00));
        assertEquals(0, group.getExpenseForMember("1").compareTo(BigDecimal.valueOf(66.66)));
        assertEquals(0, group.getExpenseForMember("2").compareTo(BigDecimal.valueOf(-33.33)));
        assertEquals(0, group.getExpenseForMember("3").compareTo(BigDecimal.valueOf(-33.33)));
        assertEquals(0, calculateTotal(group).compareTo(BigDecimal.ZERO));
    }

    @Test
    public void shouldSplitExpenseCorrectlyWithRounding() {
        Member member1 = new Member("1", "John Doe", "");
        Member member2 = new Member("2", "Jane Doe", "");
        Member member3 = new Member("3", "Jim Doe", "");
        group.addMember(member1);
        group.addMember(member2);
        group.addMember(member3);
        group.addExpense("2", BigDecimal.valueOf(100.00));
        assertEquals(0, group.getExpenseForMember("2").compareTo(BigDecimal.valueOf(66.67)));
        assertEquals(0, group.getExpenseForMember("1").compareTo(BigDecimal.valueOf(-33.34)));
        assertEquals(0, group.getExpenseForMember("3").compareTo(BigDecimal.valueOf(-33.33)));
        assertEquals(0, calculateTotal(group).compareTo(BigDecimal.ZERO));
    }

    @Test
    public void shouldThrowExceptionWhenAddingExpenseWithMoreThanTwoDecimalPlaces() {
        Member member1 = new Member("1", "John Doe", "");
        group.addMember(member1);
        assertThrows(IllegalArgumentException.class, () -> group.addExpense("1", BigDecimal.valueOf(100.123)));
    }

    @Test
    public void shouldThrowExceptionWhenSettlingUpYourself() {
        Member member1 = new Member("1", "John Doe", "");
        assertThrows(IllegalArgumentException.class, () -> group.settleUp(member1.uid(), member1.uid(), BigDecimal.valueOf(100.00)));
    }

    @Test
    public void shouldThrowExceptionWhenSettlingUpWithNonExistingMember() {
        Member member1 = new Member("1", "John Doe", "");
        group.addMember(member1);
        assertThrows(IllegalArgumentException.class, () -> group.settleUp(member1.uid(), "2", BigDecimal.valueOf(100.00)));
    }

    @Test
    public void shouldSettleUpSuccessfully() {
        Member member1 = new Member("1", "John Doe", "");
        Member member2 = new Member("2", "Jane Doe", "");
        group.addMember(member1);
        group.addMember(member2);
        group.addExpense("1", BigDecimal.valueOf(100.00));
        group.settleUp(member1.uid(), member2.uid(), BigDecimal.valueOf(50.00));
        assertEquals(0, group.getExpenseForMember("1").compareTo(BigDecimal.valueOf(0.00)));
        assertEquals(0, group.getExpenseForMember("2").compareTo(BigDecimal.valueOf(0.00)));
        assertEquals(0, calculateTotal(group).compareTo(BigDecimal.ZERO));
    }

    @Test
    public void shouldThrowExceptionWhenAddingDuplicateTransactionIDWithDifferentValue() {
        Member member1 = new Member("1", "John Doe", "");
        group.addMember(member1);
        group.addExpense("1", BigDecimal.valueOf(100.00), "txn123");
        assertThrows(IllegalArgumentException.class, () -> group.addExpense("1", BigDecimal.valueOf(50.00), "txn123"));
    }

    @Test
    public void shouldNotThrowExceptionWhenAddingDuplicateTransactionIDWithSameValue() {
        Member member1 = new Member("1", "John Doe", "");
        group.addMember(member1);
        group.addMember(new Member("2", "Jane Doe", ""));
        group.addExpense("1", BigDecimal.valueOf(100.00), "txn123");
        assertDoesNotThrow(() -> group.addExpense("1", BigDecimal.valueOf(100.00), "txn123"));
        assertEquals(0, group.getExpenseForMember("1").compareTo(BigDecimal.valueOf(50.00)));
    }

    @Test
    public void shouldThrowExceptionWhenAddingSettleUpWithTheSameKeyForAExpense() {
        Member member1 = new Member("1", "John Doe", "");
        Member member2 = new Member("2", "John Doe", "");
        group.addMember(member1);
        group.addMember(member2);
        group.addExpense("1", BigDecimal.valueOf(100.00), "txn123");
        assertThrows(IllegalArgumentException.class, () -> group.settleUp("1", "2", BigDecimal.TEN, "txn123"));
    }

    @RepeatedTest(20)
    public void shouldAddExpenseSuccessfullyConcurrent() throws ExecutionException, InterruptedException, TimeoutException {
        Member member1 = new Member("1", "John Doe", "");
        Member member2 = new Member("2", "Jane Doe", "");
        Member member3 = new Member("3", "Jim Doe", "");
        group.addMember(member1);
        group.addMember(member2);
        group.addMember(member3);

        var noThreads = 10;
        var noTasks = 1000;
        var pool = Executors.newFixedThreadPool(noThreads);
        var futures = new ArrayList<Future<?>>();
        var start = new CountDownLatch(1);
        var value = new BigDecimal("0.03");

        for (int i = 0; i < noThreads; ++i) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < noTasks / noThreads; ++j) {
                    group.addExpense("1", value);
                }
                return null;
            }));
        }

        start.countDown();
        for (var f : futures) f.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, group.getExpenseForMember("1").compareTo(new BigDecimal("20.00")));
        assertEquals(0, group.getExpenseForMember("2").compareTo(new BigDecimal("-10.00")));
        assertEquals(0, group.getExpenseForMember("3").compareTo(new BigDecimal("-10.00")));
        assertEquals(0, calculateTotal(group).compareTo(BigDecimal.ZERO));
    }

    @RepeatedTest(20)
    public void shouldSettleUpSucessfullyWithTheSameKey() throws ExecutionException, InterruptedException, TimeoutException {
        Member member1 = new Member("1", "John Doe", "");
        Member member2 = new Member("2", "Jane Doe", "");
        group.addMember(member1);
        group.addMember(member2);

        var noThreads = 10;
        var noTasks = 1000;
        var pool = Executors.newFixedThreadPool(noThreads * 2);
        var futures = new ArrayList<Future<?>>();
        var start = new CountDownLatch(1);
        var valueExpense = new BigDecimal("20.00");

        group.addExpense("1", valueExpense, "txn123");

        assertEquals(0, group.getExpenseForMember("1").compareTo(BigDecimal.TEN));
        assertEquals(0, group.getExpenseForMember("2").compareTo(BigDecimal.valueOf(-10.00)));

        start = new CountDownLatch(1);
        pool = Executors.newFixedThreadPool(noThreads);
        futures = new ArrayList<Future<?>>();

        var valueSettle = new BigDecimal("10.00");
        for (int i = 0; i < noThreads; ++i) {
            CountDownLatch finalStart = start;
            futures.add(pool.submit(() -> {
                finalStart.await();
                for (int j = 0; j < noTasks / noThreads; ++j) {
                    group.settleUp("1", "2", valueSettle, "txn456");
                }
                return null;
            }));
        }

        start.countDown();
        for (var f : futures) f.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, group.getExpenseForMember("1").compareTo(BigDecimal.ZERO));
        assertEquals(0, group.getExpenseForMember("2").compareTo(BigDecimal.ZERO));
    }
}
