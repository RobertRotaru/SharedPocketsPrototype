package org.example.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
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
        assertThrows(IllegalArgumentException.class, () -> group.addMember(member));
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
        assertThrows(IllegalArgumentException.class, () -> group.settleUp("2", member1.uid(), BigDecimal.valueOf(100.00)));
    }

    @Test
    public void shouldSettleUpSuccessfully() {
        Member member1 = new Member("1", "John Doe", "");
        Member member2 = new Member("2", "Jane Doe", "");
        group.addMember(member1);
        group.addMember(member2);
        group.addExpense("1", BigDecimal.valueOf(100.00));
        group.settleUp(member2.uid(), member1.uid(), BigDecimal.valueOf(50.00));
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
        assertThrows(IllegalArgumentException.class, () -> group.settleUp("2", "1", BigDecimal.TEN, "txn123"));
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
                    group.settleUp("2", "1", valueSettle, "txn456");
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

    // ---- Payment history (US1) ----

    private Group groupWith(TestClock clock, String... uids) {
        var g = new Group("g", "Test Group", "history", clock);
        for (var uid : uids) {
            g.addMember(new Member(uid, uid, ""));
        }
        return g;
    }

    @Test
    public void shouldRecordOneEntryPerAppliedExpenseAndSettlement() {
        var g = groupWith(TestClock.fixed(), "1", "2");

        g.addExpense("1", new BigDecimal("100.00"), "e1");
        g.settleUp("2", "1", new BigDecimal("50.00"), "s1");

        var items = g.getTransactionPage(1).items();
        assertEquals(2, items.size());
        var settlement = items.get(0);
        var expense = items.get(1);
        assertEquals(TransactionType.SETTLEMENT, settlement.type());
        assertEquals("2", settlement.payerId());
        assertEquals("1", settlement.receiverId());
        assertEquals(TransactionType.EXPENSE, expense.type());
        assertEquals("1", expense.payerId());
        assertEquals("e1", expense.idempotencyKey());
        assertEquals("s1", settlement.idempotencyKey());
        assertEquals(TestClock.START, expense.recordedAt());
        assertEquals(0, expense.amount().compareTo(new BigDecimal("100.00")));
    }

    @Test
    public void shouldReturnHistoryNewestFirst() {
        var g = groupWith(TestClock.stepping(Duration.ofSeconds(1)), "1", "2");

        g.addExpense("1", new BigDecimal("10.00"), "a");
        g.addExpense("2", new BigDecimal("20.00"), "b");
        g.addExpense("1", new BigDecimal("30.00"), "c");

        var items = g.getTransactionPage(1).items();
        assertEquals(List.of("c", "b", "a"), items.stream().map(Transaction::idempotencyKey).toList());
        assertTrue(items.get(0).recordedAt().isAfter(items.get(1).recordedAt()));
        assertTrue(items.get(1).recordedAt().isAfter(items.get(2).recordedAt()));
    }

    @Test
    public void shouldKeepLaterRecordedFirstWhenWritesShareTheSameInstant() {
        var g = groupWith(TestClock.fixed(), "1", "2");

        g.addExpense("1", new BigDecimal("1.00"), "a");
        g.addExpense("1", new BigDecimal("2.00"), "b");

        var keys = g.getTransactionPage(1).items().stream().map(Transaction::idempotencyKey).toList();
        assertEquals(List.of("b", "a"), keys);
    }

    @Test
    public void shouldRecordKeylessCallsWithNullIdempotencyKey() {
        var g = groupWith(TestClock.fixed(), "1", "2");

        g.addExpense("1", new BigDecimal("10.00"));
        g.settleUp("2", "1", new BigDecimal("5.00"));

        var items = g.getTransactionPage(1).items();
        assertEquals(2, items.size());
        assertNull(items.get(0).idempotencyKey());
        assertNull(items.get(1).idempotencyKey());
    }

    @Test
    public void shouldGenerateUniqueNonNullIds() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        for (int i = 0; i < 15; i++) {
            g.addExpense("1", new BigDecimal("1.00"), "k" + i);
        }

        var ids = new java.util.HashSet<String>();
        g.getTransactionPage(1).items().forEach(t -> ids.add(t.id()));
        g.getTransactionPage(2).items().forEach(t -> ids.add(t.id()));
        assertEquals(15, ids.size());
        assertFalse(ids.contains(null));
    }

    @Test
    public void shouldPageGroupHistoryAsTenTenFive() {
        var g = groupWith(TestClock.stepping(Duration.ofSeconds(1)), "1", "2");
        for (int i = 1; i <= 25; i++) {
            g.addExpense("1", new BigDecimal("1.00"), "k" + i);
        }

        var p1 = g.getTransactionPage(1);
        var p2 = g.getTransactionPage(2);
        var p3 = g.getTransactionPage(3);
        assertEquals(List.of(10, 10, 5), List.of(p1.items().size(), p2.items().size(), p3.items().size()));
        assertEquals("k25", p1.items().getFirst().idempotencyKey());
        assertEquals("k1", p3.items().getLast().idempotencyKey());
        assertTrue(p1.hasNext() && p2.hasNext() && !p3.hasNext());
    }

    @Test
    public void shouldNotAddSecondEntryNorChangeTimestampOnIdempotentReplay() {
        var g = groupWith(TestClock.stepping(Duration.ofSeconds(1)), "1", "2");

        g.addExpense("1", new BigDecimal("100.00"), "e1");
        var original = g.getTransactionPage(1).items().getFirst();
        g.addExpense("1", new BigDecimal("100.00"), "e1");

        var items = g.getTransactionPage(1).items();
        assertEquals(1, items.size());
        assertEquals(original, items.getFirst());
        assertEquals(0, g.getExpenseForMember("1").compareTo(new BigDecimal("50.00")));
    }

    @Test
    public void shouldAddNoEntryWhenKeyIsReusedWithDifferentRequest() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        g.addExpense("1", new BigDecimal("100.00"), "e1");

        assertThrows(ConflictException.class, () -> g.addExpense("1", new BigDecimal("999.00"), "e1"));
        assertThrows(ConflictException.class, () -> g.settleUp("2", "1", new BigDecimal("5.00"), "e1"));

        assertEquals(1, g.getTransactionPage(1).items().size());
        assertEquals(0, calculateTotal(g).compareTo(BigDecimal.ZERO));
    }

    @Test
    public void shouldAddNoEntryAndKeepKeyUsableWhenRequestIsRejected() {
        var g = groupWith(TestClock.fixed(), "1", "2");

        assertThrows(IllegalArgumentException.class, () -> g.addExpense("nobody", new BigDecimal("10.00"), "k"));
        assertThrows(IllegalArgumentException.class, () -> g.addExpense("1", BigDecimal.ZERO, "k"));
        assertThrows(IllegalArgumentException.class, () -> g.addExpense("1", new BigDecimal("-1.00"), "k"));
        assertThrows(IllegalArgumentException.class, () -> g.addExpense("1", new BigDecimal("1.005"), "k"));
        assertThrows(IllegalArgumentException.class, () -> g.settleUp("1", "1", new BigDecimal("5.00"), "k"));
        assertThrows(IllegalArgumentException.class, () -> g.settleUp("1", "ghost", new BigDecimal("5.00"), "k"));
        assertThrows(IllegalArgumentException.class, () -> g.settleUp("ghost", "1", new BigDecimal("5.00"), "k"));

        assertTrue(g.getTransactionPage(1).items().isEmpty());
        assertEquals(0, calculateTotal(g).compareTo(BigDecimal.ZERO));
        // the same key still works once the request is corrected
        g.addExpense("1", new BigDecimal("10.00"), "k");
        assertEquals(1, g.getTransactionPage(1).items().size());
        assertEquals(0, calculateTotal(g).compareTo(BigDecimal.ZERO));
    }

    // ---- Descriptions (US2) ----

    private static String descriptionOfNewest(Group g) {
        return g.getTransactionPage(1).items().getFirst().description();
    }

    @Test
    public void shouldStoreProvidedDescriptionExactly() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        var text = "Café à Lisboa 🍽 <b>&\"quotes\"</b>";

        g.addExpense("1", new BigDecimal("10.00"), "e1", text);
        g.settleUp("2", "1", new BigDecimal("5.00"), "s1", "Paying back €5");

        assertEquals("Paying back €5", descriptionOfNewest(g));
        assertEquals(text, g.getTransactionPage(1).items().get(1).description());
    }

    @Test
    public void shouldTrimDescription() {
        var g = groupWith(TestClock.fixed(), "1", "2");

        g.addExpense("1", new BigDecimal("10.00"), "e1", "   Dinner \t");

        assertEquals("Dinner", descriptionOfNewest(g));
    }

    @Test
    public void shouldAcceptTwoHundredCharactersAndRejectTwoHundredAndOne() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        var max = "x".repeat(200);

        g.addExpense("1", new BigDecimal("1.00"), "ok", max);
        assertEquals(max, descriptionOfNewest(g));

        assertThrows(IllegalArgumentException.class, () -> g.addExpense("1", new BigDecimal("1.00"), "too-long", max + "x"));
        assertThrows(IllegalArgumentException.class, () -> g.settleUp("2", "1", new BigDecimal("1.00"), "too-long-s", max + "x"));
        assertEquals(1, g.getTransactionPage(1).items().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n "})
    public void shouldRejectEmptyOrWhitespaceOnlyDescription(String bad) {
        var g = groupWith(TestClock.fixed(), "1", "2");

        assertThrows(IllegalArgumentException.class, () -> g.addExpense("1", new BigDecimal("1.00"), "e", bad));
        assertThrows(IllegalArgumentException.class, () -> g.settleUp("2", "1", new BigDecimal("1.00"), "s", bad));
    }

    @Test
    public void shouldLeaveNoTraceWhenDescriptionIsRejected() {
        var g = groupWith(TestClock.fixed(), "1", "2");

        assertThrows(IllegalArgumentException.class, () -> g.addExpense("1", new BigDecimal("10.00"), "k", "   "));

        assertTrue(g.getTransactionPage(1).items().isEmpty());
        assertEquals(0, g.getExpenseForMember("1").compareTo(BigDecimal.ZERO));
        assertEquals(0, g.getExpenseForMember("2").compareTo(BigDecimal.ZERO));
        // the key was not consumed: the corrected request with the same key succeeds
        g.addExpense("1", new BigDecimal("10.00"), "k", "Dinner");
        assertEquals("Dinner", descriptionOfNewest(g));
    }

    @Test
    public void shouldConflictWhenSameKeyAndAmountButDifferentDescription() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        g.addExpense("1", new BigDecimal("10.00"), "k", "Dinner");

        assertThrows(ConflictException.class, () -> g.addExpense("1", new BigDecimal("10.00"), "k", "Lunch"));

        assertEquals(1, g.getTransactionPage(1).items().size());
        assertEquals("Dinner", descriptionOfNewest(g));
    }

    @Test
    public void shouldTreatSameKeyAndSameDescriptionAsIdempotentReplay() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        g.addExpense("1", new BigDecimal("10.00"), "k", "Dinner");

        assertDoesNotThrow(() -> g.addExpense("1", new BigDecimal("10.00"), "k", "  Dinner  "));

        assertEquals(1, g.getTransactionPage(1).items().size());
        assertEquals(0, g.getExpenseForMember("1").compareTo(new BigDecimal("5.00")));
    }

    @Test
    public void shouldTreatNullDescriptionLikeOmittedAndUseDefaultText() {
        var g = groupWith(TestClock.fixed(), "1", "2");

        g.addExpense("1", new BigDecimal("10.00"), "e", null);
        g.settleUp("2", "1", new BigDecimal("5.00"), "s", null);

        assertEquals("Settlement", descriptionOfNewest(g));
        assertEquals("Expense", g.getTransactionPage(1).items().get(1).description());
        // a replay through the omitted-description overload is a no-op
        assertDoesNotThrow(() -> g.addExpense("1", new BigDecimal("10.00"), "e"));
        assertEquals(2, g.getTransactionPage(1).items().size());
    }

    // ---- Backward compatibility (US3) ----

    @Test
    public void shouldKeepEveryPreFeatureSignatureWorking() {
        var g = new Group("g", "Legacy", "three-argument constructor");
        g.addMember(new Member("1", "One", ""));
        g.addMember(new Member("2", "Two", ""));

        g.addExpense("1", new BigDecimal("100.00"));
        g.addExpense("1", new BigDecimal("100.00"), "e1");
        g.settleUp("2", "1", new BigDecimal("10.00"));
        g.settleUp("2", "1", new BigDecimal("10.00"), "s1");

        assertEquals(0, g.getExpenseForMember("1").compareTo(new BigDecimal("80.00")));
        assertEquals(0, g.getExpenseForMember("2").compareTo(new BigDecimal("-80.00")));
        assertEquals(0, calculateTotal(g).compareTo(BigDecimal.ZERO));
        assertEquals(4, g.getTransactionPage(1).items().size());
    }

    @Test
    public void shouldReplayAcrossOldAndNewOverloadsWhenNoDescriptionIsGiven() {
        var g = groupWith(TestClock.fixed(), "1", "2");

        g.addExpense("1", new BigDecimal("10.00"), "a");
        g.addExpense("1", new BigDecimal("10.00"), "a", null);
        g.addExpense("1", new BigDecimal("10.00"), "b", null);
        g.addExpense("1", new BigDecimal("10.00"), "b");
        g.settleUp("2", "1", new BigDecimal("1.00"), "c");
        g.settleUp("2", "1", new BigDecimal("1.00"), "c", null);

        assertEquals(3, g.getTransactionPage(1).items().size());
        assertEquals(0, g.getExpenseForMember("1").compareTo(new BigDecimal("9.00")));
    }

    @Test
    public void shouldStillConflictBeforeValidatingWhenKeyIsReusedWithADifferentInvalidBody() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        g.addExpense("1", new BigDecimal("10.00"), "k");

        // exactly as before this feature: the key check wins over amount validation
        assertThrows(ConflictException.class, () -> g.addExpense("1", new BigDecimal("-5.00"), "k"));
        assertThrows(ConflictException.class, () -> g.addExpense("1", new BigDecimal("1.234"), "k"));
        assertThrows(ConflictException.class, () -> g.settleUp("2", "2", new BigDecimal("1.00"), "k"));
        assertEquals(1, g.getTransactionPage(1).items().size());
    }

    @Test
    public void shouldConflictWhenMixingExpenseAndSettlementUnderOneKeyAcrossOverloads() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        g.addExpense("1", new BigDecimal("10.00"), "k", "Dinner");

        assertThrows(ConflictException.class, () -> g.settleUp("2", "1", new BigDecimal("10.00"), "k", "Dinner"));
        assertEquals(1, g.getTransactionPage(1).items().size());
    }

    // ---- Cache correctness at group level (US4) ----

    @Test
    public void shouldKeepServingTheSamePageAfterRejectedWritesAndReplays() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        g.addExpense("1", new BigDecimal("10.00"), "k", "Dinner");
        var before = g.getTransactionPage(1);

        assertThrows(IllegalArgumentException.class, () -> g.addExpense("1", BigDecimal.ZERO, "bad"));
        assertThrows(IllegalArgumentException.class, () -> g.addExpense("1", new BigDecimal("1.00"), "bad2", "  "));
        assertThrows(ConflictException.class, () -> g.addExpense("1", new BigDecimal("99.00"), "k"));
        g.addExpense("1", new BigDecimal("10.00"), "k", "Dinner"); // replay

        assertSame(before, g.getTransactionPage(1));
        assertEquals(1, before.items().size());
    }

    @Test
    public void shouldShowANewTransactionOnTheVeryNextRead() {
        var g = groupWith(TestClock.stepping(Duration.ofSeconds(1)), "1", "2");
        g.addExpense("1", new BigDecimal("10.00"), "a");
        var cached = g.getTransactionPage(1);

        g.settleUp("2", "1", new BigDecimal("1.00"), "b");

        var next = g.getTransactionPage(1);
        assertNotSame(cached, next);
        assertEquals(2, next.items().size());
        assertEquals("b", next.items().getFirst().idempotencyKey());
    }

    @Test
    public void shouldNotAffectAnotherGroupsCachedPage() {
        var a = groupWith(TestClock.fixed(), "1", "2");
        var b = groupWith(TestClock.fixed(), "1", "2");
        a.addExpense("1", new BigDecimal("10.00"), "a1");
        var cachedA = a.getTransactionPage(1);

        b.addExpense("1", new BigDecimal("20.00"), "b1");

        assertSame(cachedA, a.getTransactionPage(1));
        assertEquals(1, b.getTransactionPage(1).items().size());
    }

    @Test
    public void shouldConflictWhenOmittedFirstAndLiteralDefaultTextSecond_andReverse() {
        var g = groupWith(TestClock.fixed(), "1", "2");
        g.addExpense("1", new BigDecimal("10.00"), "a");
        g.addExpense("1", new BigDecimal("10.00"), "b", "Expense");

        assertThrows(ConflictException.class, () -> g.addExpense("1", new BigDecimal("10.00"), "a", "Expense"));
        assertThrows(ConflictException.class, () -> g.addExpense("1", new BigDecimal("10.00"), "b"));
        assertEquals(2, g.getTransactionPage(1).items().size());
    }
}
