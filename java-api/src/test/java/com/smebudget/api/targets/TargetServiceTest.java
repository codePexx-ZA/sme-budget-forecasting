package com.smebudget.api.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smebudget.api.auth.AppUser;
import com.smebudget.api.auth.AppUserRepository;
import com.smebudget.api.auth.AuthenticatedUser;
import com.smebudget.api.common.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** KPI target rules. Repositories are in-memory fakes, so no database is required. */
class TargetServiceTest {

    private static final String UNIT = "20000000000000000000000000000001";
    private static final String OTHER_UNIT = "20000000000000000000000000000099";
    private static final String DECISION_ID = "30000000000000000000000000000003";
    private static final String SUBMITTER_ID = "30000000000000000000000000000001";

    private final AuthenticatedUser decisionMaker = new AuthenticatedUser(DECISION_ID, "decision_maker", "token-d");
    private final AuthenticatedUser submitter = new AuthenticatedUser(SUBMITTER_ID, "submitter", "token-s");

    private final Map<String, KpiTarget> stored = new HashMap<>();
    private final Map<String, AppUser> storedUsers = new HashMap<>();

    private TargetService service;

    @BeforeEach
    void setUp() {
        stored.clear();
        storedUsers.clear();
        storedUsers.put(DECISION_ID, user(DECISION_ID, UNIT, "decision_maker"));
        storedUsers.put(SUBMITTER_ID, user(SUBMITTER_ID, UNIT, "submitter"));

        KpiTargetRepository targets = mock(KpiTargetRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);

        when(targets.save(any())).thenAnswer(invocation -> {
            KpiTarget target = invocation.getArgument(0);
            stored.put(target.getTargetId(), target);
            return target;
        });
        when(targets.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(targets.findByUnitIdOrderByTargetPeriodDescMetricNameAsc(anyString())).thenAnswer(invocation -> {
            String unitId = invocation.getArgument(0);
            return stored.values().stream().filter(target -> unitId.equals(target.getUnitId())).toList();
        });
        when(targets.findByUnitIdAndMetricNameAndTargetPeriod(anyString(), anyString(), any())).thenAnswer(invocation -> {
            String unitId = invocation.getArgument(0);
            String name = invocation.getArgument(1);
            LocalDate period = invocation.getArgument(2);
            return stored.values().stream()
                    .filter(target -> unitId.equals(target.getUnitId())
                            && name.equals(target.getMetricName())
                            && period.equals(target.getTargetPeriod()))
                    .findFirst();
        });
        doAnswer(invocation -> {
            KpiTarget target = invocation.getArgument(0);
            stored.remove(target.getTargetId());
            return null;
        }).when(targets).delete(any());
        when(users.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(storedUsers.get(invocation.getArgument(0))));

        service = new TargetService(targets, users);
    }

    @Test
    void decisionMakerCanCreateAndListATarget() {
        KpiTargetResponse created = service.create(decisionMaker, request("Revenue", "5800000", "ZAR", "2026-03-01"));

        assertEquals("Revenue", created.metricName());
        assertEquals(new BigDecimal("5800000.00"), created.targetValue());
        assertEquals(1, service.list(decisionMaker).size());
        assertEquals(created.targetId(), service.get(decisionMaker, created.targetId()).targetId());
    }

    @Test
    void submitterCannotCreate() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.create(submitter, request("Revenue", "1", "ZAR", "2026-03-01")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void duplicateNameAndPeriodIsRejected() {
        service.create(decisionMaker, request("Revenue", "1", "ZAR", "2026-03-01"));
        ApiException error = assertThrows(ApiException.class,
                () -> service.create(decisionMaker, request("Revenue", "2", "ZAR", "2026-03-01")));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void updateChangesTheValueAndDeleteRemovesIt() {
        KpiTargetResponse created = service.create(decisionMaker, request("Revenue", "1", "ZAR", "2026-03-01"));
        KpiTargetResponse updated = service.update(decisionMaker, created.targetId(),
                request("Revenue", "2.5", "ZAR", "2026-03-01"));

        assertEquals(new BigDecimal("2.50"), updated.targetValue());
        service.delete(decisionMaker, created.targetId());
        assertEquals(0, service.list(decisionMaker).size());
    }

    @Test
    void targetFromAnotherUnitIsHidden() {
        stored.put("other", new KpiTarget("other", OTHER_UNIT, "Revenue", new BigDecimal("1.00"), "ZAR",
                LocalDate.parse("2026-03-01")));
        ApiException error = assertThrows(ApiException.class, () -> service.get(decisionMaker, "other"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    private static KpiTargetRequest request(String name, String value, String unit, String period) {
        return new KpiTargetRequest(name, new BigDecimal(value), unit, LocalDate.parse(period));
    }

    private static AppUser user(String id, String unitId, String role) {
        return new AppUser(id, unitId, "Test User", id + "@example.com", "hash", role, "active");
    }
}
