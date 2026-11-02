package org.example.domain;

import java.math.BigDecimal;

public class GroupTestHelper {
    public static BigDecimal calculateTotal(Group group) {
        BigDecimal total = BigDecimal.ZERO;
        for (var member : group.getMembers()) {
            total = total.add(group.getExpenseForMember(member.uid()));
        }
        return total;
    }
}
