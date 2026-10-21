package com.smebudget.api.submissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for budget submissions. Repositories are in-memory fakes, so no database is required.
 */
class SubmissionServiceTest {

    private static final String UNIT = "20000000000000000000000000000001";
    private static final String OTHER_UNIT = "20000000000000000000000000000099";
    private static final String SUBMITTER_ID = "30000000000000000000000000000001";
    private static final String APPROVER_ID = "30000000000000000000000000000002";
    private static final String DECISION_ID = "30000000000000000000000000000003";

    private final AuthenticatedUser submitter = new AuthenticatedUser(SUBMITTER_ID, "submitter", "token-s");
    private final AuthenticatedUser approver = new AuthenticatedUser(APPROVER_ID, "approver", "token-a");
    private final AuthenticatedUser decisionMaker = new AuthenticatedUser(DECISION_ID, "decision_maker", "token-d");

    private final Map<String, BudgetSubmission> storedSubmissions = new HashMap<>();
    private final List<FinancialLineItem> storedLines = new ArrayList<>();
    private final List<ApprovalAction> storedApprovals = new ArrayList<>();
    private final Map<String, AppUser> storedUsers = new HashMap<>();

    private SubmissionService service;

    @BeforeEach
    void setUp() {
        storedSubmissions.clear();
        storedLines.clear();
        storedApprovals.clear();
        storedUsers.clear();
        storedUsers.put(SUBMITTER_ID, user(SUBMITTER_ID, UNIT, "Sam Submitter", "submitter"));
        storedUsers.put(APPROVER_ID, user(APPROVER_ID, UNIT, "Alex Approver", "approver"));
        storedUsers.put(DECISION_ID, user(DECISION_ID, UNIT, "Dana Decision", "decision_maker"));

        BudgetSubmissionRepository submissions = mock(BudgetSubmissionRepository.class);
        FinancialLineItemRepository lineItems = mock(FinancialLineItemRepository.class);
        ApprovalActionRepository approvals = mock(ApprovalActionRepository.class);
        LineCategoryRepository categories = mock(LineCategoryRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);

        when(submissions.save(any())).thenAnswer(invocation -> {
            BudgetSubmission submission = invocation.getArgument(0);
            storedSubmissions.put(submission.getSubmissionId(), submission);
            return submission;
        });
        when(submissions.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(storedSubmissions.get(invocation.getArgument(0))));
        when(submissions.findByUnitId(anyString())).thenAnswer(invocation -> {
            String unitId = invocation.getArgument(0);
            List<BudgetSubmission> found = new ArrayList<>();
            for (BudgetSubmission submission : storedSubmissions.values()) {
                if (unitId.equals(submission.getUnitId())) {
                    found.add(submission);
                }
            }
            return found;
        });
        when(submissions.existsByReplacesSubmissionId(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            for (BudgetSubmission submission : storedSubmissions.values()) {
                if (id.equals(submission.getReplacesSubmissionId())) {
                    return true;
                }
            }
            return false;
        });
        doAnswer(invocation -> {
            BudgetSubmission submission = invocation.getArgument(0);
            storedSubmissions.remove(submission.getSubmissionId());
            return null;
        }).when(submissions).delete(any());

        when(lineItems.save(any())).thenAnswer(invocation -> {
            FinancialLineItem line = invocation.getArgument(0);
            storedLines.removeIf(existing -> existing.getLineItemId().equals(line.getLineItemId()));
            storedLines.add(line);
            return line;
        });
        when(lineItems.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return storedLines.stream().filter(line -> line.getLineItemId().equals(id)).findFirst();
        });
        when(lineItems.findBySubmissionId(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            List<FinancialLineItem> found = new ArrayList<>();
            for (FinancialLineItem line : storedLines) {
                if (line.getSubmissionId().equals(id)) {
                    found.add(line);
                }
            }
            return found;
        });
        doAnswer(invocation -> {
            Iterable<?> items = invocation.getArgument(0);
            for (Object item : items) {
                storedLines.remove(item);
            }
            return null;
        }).when(lineItems).deleteAll(any());
        doAnswer(invocation -> {
            FinancialLineItem line = invocation.getArgument(0);
            storedLines.remove(line);
            return null;
        }).when(lineItems).delete(any());

        when(approvals.save(any())).thenAnswer(invocation -> {
            ApprovalAction action = invocation.getArgument(0);
            storedApprovals.add(action);
            return action;
        });
        when(approvals.findBySubmissionId(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            List<ApprovalAction> found = new ArrayList<>();
            for (ApprovalAction action : storedApprovals) {
                if (action.getSubmissionId().equals(id)) {
                    found.add(action);
                }
            }
            return found;
        });
        doAnswer(invocation -> {
            Iterable<?> items = invocation.getArgument(0);
            for (Object item : items) {
                storedApprovals.remove(item);
            }
            return null;
        }).when(approvals).deleteAll(any());

        when(categories.findAllByOrderBySortOrderAsc()).thenReturn(sampleCategories());
        when(users.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(storedUsers.get(invocation.getArgument(0))));

        service = new SubmissionService(submissions, lineItems, approvals, categories, users);
    }

    @Test
    void submitterCreatesDraftWithDefaults() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), null, null, null, null,
                List.of(line("revenue", date(2026, 7, 1), "100.5", null))));

        assertEquals(32, created.submissionId().length());
        assertTrue(created.submissionId().chars().noneMatch(ch -> ch == '-'));
        assertEquals(BudgetSubmission.STATUS_DRAFT, created.status());
        assertNull(created.submittedAt());
        assertEquals(BudgetSubmission.IMPORT_MANUAL, created.importMethod());
        assertEquals(1, created.sourceScale());
        assertEquals(SUBMITTER_ID, created.submittedBy());
        assertEquals(UNIT, created.unitId());
        assertEquals(new BigDecimal("100.50"), created.lineItems().get(0).amount());
        assertEquals(FinancialLineItem.ANNUAL, created.lineItems().get(0).frequency());
        assertEquals("Revenue", created.lineItems().get(0).categoryName());
    }

    @Test
    void approverAndDecisionMakerCannotCreate() {
        SubmissionRequest body = request(date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null, List.of());

        ApiException approverDenied = assertThrows(ApiException.class, () -> service.create(approver, body));
        ApiException decisionDenied = assertThrows(ApiException.class, () -> service.create(decisionMaker, body));

        assertEquals(HttpStatus.FORBIDDEN, approverDenied.getStatus());
        assertEquals(SubmissionService.FORBIDDEN, approverDenied.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, decisionDenied.getStatus());
    }

    @Test
    void listAndGetStayInsideTheBusinessUnit() {
        SubmissionResponse own = service.create(submitter, request(
                date(2026, 6, 1), date(2026, 6, 30), "manual", null, 1, null, List.of()));
        storedSubmissions.put("other-submission", submission(
                "other-submission", OTHER_UNIT, SUBMITTER_ID, date(2026, 1, 1), date(2026, 1, 31),
                BudgetSubmission.STATUS_DRAFT, null, null));

        List<SubmissionSummary> listed = service.list(decisionMaker);

        assertEquals(1, listed.size());
        assertEquals(own.submissionId(), listed.get(0).submissionId());
        assertEquals("Sam Submitter", service.get(approver, own.submissionId()).submitterName());
        ApiException hidden = assertThrows(ApiException.class, () -> service.get(submitter, "other-submission"));
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatus());
    }

    @Test
    void linesAreOrderedByPeriodThenCategory() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 3, 1), date(2026, 4, 30), "manual", null, 1, null,
                List.of(
                        line("cost_of_sales", date(2026, 3, 1), "20", "monthly"),
                        line("revenue", date(2026, 4, 1), "80", "monthly"),
                        line("revenue", date(2026, 3, 1), "40", "monthly"))));

        assertEquals(List.of("revenue", "cost_of_sales", "revenue"),
                created.lineItems().stream().map(LineItemResponse::category).toList());
        assertEquals(List.of(date(2026, 3, 1), date(2026, 3, 1), date(2026, 4, 1)),
                created.lineItems().stream().map(LineItemResponse::periodStart).toList());
    }

    @Test
    void totalsFollowTheIncomeStatementFormula() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 3, 1), date(2026, 3, 31), "manual", null, 1, null,
                List.of(
                        line("revenue", date(2026, 3, 1), "100", "monthly"),
                        line("other_income", date(2026, 3, 1), "10", "monthly"),
                        line("cost_of_sales", date(2026, 3, 1), "40", "monthly"),
                        line("employee_costs", date(2026, 3, 1), "20", "monthly"),
                        line("finance_costs", date(2026, 3, 1), "5", "monthly"),
                        line("income_tax", date(2026, 3, 1), "3", "monthly"))));

        PeriodTotals totals = created.totals().get(0);
        assertEquals(new BigDecimal("70.00"), totals.grossProfit());
        assertEquals(new BigDecimal("50.00"), totals.operatingProfit());
        assertEquals(new BigDecimal("42.00"), totals.netProfit());
    }

    @Test
    void invalidHeaderAndLinesAreRejected() {
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.PERIOD_ORDER, () -> service.create(submitter, request(
                date(2026, 8, 1), date(2026, 7, 1), "manual", null, 1, null, List.of())));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.BAD_SCALE, () -> service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 100, null, List.of())));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.BAD_IMPORT, () -> service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "paste", null, 1, null, List.of())));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.LINE_OUTSIDE, () -> service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 8, 1), "10", "monthly")))));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.DUPLICATE_LINE, () -> service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(
                        line("revenue", date(2026, 7, 1), "10", "monthly"),
                        line("revenue", date(2026, 7, 1), "12", "monthly")))));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.UNKNOWN_CATEGORY, () -> service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("wages", date(2026, 7, 1), "10", "monthly")))));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.BAD_FREQUENCY, () -> service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 7, 1), "10", "weekly")))));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.BAD_AMOUNT_SCALE, () -> service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 7, 1), "10.555", "monthly")))));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.FILE_NAME_LONG, () -> service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", "x".repeat(256), 1, null, List.of())));
    }

    @Test
    void boundaryDatesAndTrailingZerosAreAccepted() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "template", "  year.xlsx  ", 1000, null,
                List.of(line("revenue", date(2026, 7, 31), "10.500", "monthly"))));

        assertEquals("year.xlsx", created.sourceFileName());
        assertEquals(1000, created.sourceScale());
        assertEquals(new BigDecimal("10.50"), created.lineItems().get(0).amount());
    }

    @Test
    void updateReplacesLinesAndReopensARejectedSubmission() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 7, 1), "10", "monthly"))));
        service.submit(submitter, created.submissionId());
        service.reject(approver, created.submissionId(), "Fix the sales figure.");

        SubmissionResponse updated = service.update(submitter, created.submissionId(), request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 7, 1), "12", "monthly"))));

        assertEquals(BudgetSubmission.STATUS_DRAFT, updated.status());
        assertNull(updated.submittedAt());
        assertEquals(new BigDecimal("12.00"), updated.lineItems().get(0).amount());
        assertEquals(1, updated.approvals().size());
        assertEquals("Fix the sales figure.", updated.approvals().get(0).comments());
    }

    @Test
    void updateWithoutLinesKeepsThemAndEmptyListClearsThem() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 7, 1), "10", "monthly"))));

        SubmissionResponse kept = service.update(submitter, created.submissionId(), request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", "notes.pdf", 1000, null, null));
        assertEquals(1, kept.lineItems().size());
        assertEquals("notes.pdf", kept.sourceFileName());
        assertEquals(1000, kept.sourceScale());

        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.LINE_OUTSIDE, () -> service.update(
                submitter, created.submissionId(), request(
                        date(2026, 8, 1), date(2026, 8, 31), "manual", null, 1, null, null)));

        SubmissionResponse cleared = service.update(submitter, created.submissionId(), request(
                date(2026, 8, 1), date(2026, 8, 31), "manual", null, 1, null, List.of()));
        assertTrue(cleared.lineItems().isEmpty());
    }

    @Test
    void submittedAndApprovedBudgetsCannotBeEditedOrDeleted() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 7, 1), "10", "monthly"))));
        service.submit(submitter, created.submissionId());

        SubmissionRequest change = request(date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null, List.of());
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.ONLY_EDIT,
                () -> service.update(submitter, created.submissionId(), change));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.ONLY_DELETE,
                () -> service.delete(submitter, created.submissionId()));

        service.approve(approver, created.submissionId(), "Looks right.");
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.ONLY_SUBMIT,
                () -> service.submit(submitter, created.submissionId()));
    }

    @Test
    void deleteRemovesADraftButKeepsOneThatWasReplaced() {
        SubmissionResponse older = service.create(submitter, request(
                date(2026, 5, 1), date(2026, 5, 31), "manual", null, 1, null, List.of()));
        SubmissionResponse newer = service.create(submitter, request(
                date(2026, 5, 1), date(2026, 5, 31), "manual", null, 1, older.submissionId(), List.of()));

        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.KEPT_BECAUSE_REPLACED,
                () -> service.delete(submitter, older.submissionId()));

        service.delete(submitter, newer.submissionId());
        assertMessage(HttpStatus.NOT_FOUND, SubmissionService.NOT_FOUND,
                () -> service.get(submitter, newer.submissionId()));
        service.delete(submitter, older.submissionId());
        assertTrue(storedLines.stream().noneMatch(line -> line.getSubmissionId().equals(older.submissionId())));
    }

    @Test
    void lineItemCrudAndDuplicateProtection() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 8, 31), "manual", null, 1, null, List.of()));

        LineItemResponse added = service.addLineItem(submitter, created.submissionId(),
                line("revenue", date(2026, 7, 1), "15", "monthly"));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.DUPLICATE_LINE, () -> service.addLineItem(
                submitter, created.submissionId(), line("revenue", date(2026, 7, 1), "16", "monthly")));

        LineItemResponse updated = service.updateLineItem(submitter, created.submissionId(), added.lineItemId(),
                line("revenue", date(2026, 8, 1), "18", "quarterly"));
        assertEquals(date(2026, 8, 1), updated.periodStart());
        assertEquals(FinancialLineItem.QUARTERLY, updated.frequency());

        service.deleteLineItem(submitter, created.submissionId(), added.lineItemId());
        assertTrue(service.get(submitter, created.submissionId()).lineItems().isEmpty());
        assertMessage(HttpStatus.NOT_FOUND, SubmissionService.LINE_NOT_FOUND, () -> service.deleteLineItem(
                submitter, created.submissionId(), added.lineItemId()));
    }

    @Test
    void submitRequiresALineAndThenWaitsForTheApprover() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null, List.of()));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.ONLY_APPROVE,
                () -> service.approve(approver, created.submissionId(), "Too soon."));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.NEED_A_LINE,
                () -> service.submit(submitter, created.submissionId()));

        service.addLineItem(submitter, created.submissionId(), line("electricity", date(2026, 7, 1), "90", "monthly"));
        SubmissionResponse submitted = service.submit(submitter, created.submissionId());

        assertEquals(BudgetSubmission.STATUS_SUBMITTED, submitted.status());
        assertNotNull(submitted.submittedAt());
        assertMessage(HttpStatus.FORBIDDEN, SubmissionService.FORBIDDEN,
                () -> service.approve(submitter, created.submissionId(), "no"));
        assertMessage(HttpStatus.NOT_FOUND, SubmissionService.NOT_FOUND,
                () -> service.approve(approver, "missing", "no"));
    }

    @Test
    void approveSupersedesTheApprovedVersionItReplaces() {
        SubmissionResponse previous = service.create(submitter, request(
                date(2025, 3, 1), date(2026, 2, 28), "manual", null, 1, null,
                List.of(line("revenue", date(2025, 3, 1), "1000", "annual"))));
        service.submit(submitter, previous.submissionId());
        service.approve(approver, previous.submissionId(), "First version.");

        SubmissionResponse corrected = service.create(submitter, request(
                date(2025, 3, 1), date(2026, 2, 28), "manual", "signed.pdf", 1, previous.submissionId(),
                List.of(line("revenue", date(2025, 3, 1), "1100", "annual"))));
        service.submit(submitter, corrected.submissionId());
        SubmissionResponse approved = service.approve(approver, corrected.submissionId(), "  Checked.  ");

        assertEquals(BudgetSubmission.STATUS_APPROVED, approved.status());
        assertEquals("Checked.", approved.approvals().get(0).comments());
        assertEquals("Alex Approver", approved.approvals().get(0).actorName());
        assertEquals(BudgetSubmission.STATUS_SUPERSEDED, storedSubmissions.get(previous.submissionId()).getStatus());
        assertTrue(approved.replacesLabel().contains("approved") || approved.replacesLabel().contains("superseded"));
    }

    @Test
    void approveDoesNotSupersedeARejectedPredecessor() {
        SubmissionResponse previous = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 7, 1), "10", "monthly"))));
        service.submit(submitter, previous.submissionId());
        service.reject(approver, previous.submissionId(), "Use the signed pack.");

        SubmissionResponse corrected = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, previous.submissionId(),
                List.of(line("revenue", date(2026, 7, 1), "11", "monthly"))));
        service.submit(submitter, corrected.submissionId());
        service.approve(approver, corrected.submissionId(), null);

        assertEquals(BudgetSubmission.STATUS_REJECTED, storedSubmissions.get(previous.submissionId()).getStatus());
    }

    @Test
    void rejectRequiresACommentAndSubmitterCannotReject() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 7, 1), "10", "monthly"))));
        service.submit(submitter, created.submissionId());

        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.REJECTION_NEEDS_COMMENT,
                () -> service.reject(approver, created.submissionId(), "   "));
        assertMessage(HttpStatus.FORBIDDEN, SubmissionService.FORBIDDEN,
                () -> service.reject(submitter, created.submissionId(), "No"));
        assertMessage(HttpStatus.NOT_FOUND, SubmissionService.NOT_FOUND,
                () -> service.reject(approver, created.submissionId() + "-no", "No"));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.ONLY_REJECT, () -> {
            SubmissionResponse draft = service.create(submitter, request(
                    date(2026, 8, 1), date(2026, 8, 31), "manual", null, 1, null, List.of()));
            service.reject(approver, draft.submissionId(), "Too early.");
        });
    }

    @Test
    void replacementCannotLoopOrLeaveTheUnit() {
        SubmissionResponse first = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null, List.of()));
        SubmissionResponse second = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, first.submissionId(), List.of()));

        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.SELF_REPLACE, () -> service.update(
                submitter, first.submissionId(), request(
                        date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, first.submissionId(), null)));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.REPLACE_LOOP, () -> service.update(
                submitter, first.submissionId(), request(
                        date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, second.submissionId(), null)));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.REPLACE_MISSING, () -> service.create(
                submitter, request(date(2026, 9, 1), date(2026, 9, 30), "manual", null, 1, "does-not-exist", List.of())));

        storedSubmissions.put("foreign", submission(
                "foreign", OTHER_UNIT, SUBMITTER_ID, date(2026, 1, 1), date(2026, 1, 31),
                BudgetSubmission.STATUS_APPROVED, Instant.parse("2026-02-01T00:00:00Z"), null));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.REPLACE_OTHER_UNIT, () -> service.create(
                submitter, request(date(2026, 9, 1), date(2026, 9, 30), "manual", null, 1, "foreign", List.of())));
    }

    @Test
    void changingALineOnASubmittedBudgetIsRejected() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 7, 1), date(2026, 7, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 7, 1), "10", "monthly"))));
        String lineId = created.lineItems().get(0).lineItemId();
        service.submit(submitter, created.submissionId());

        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.ONLY_EDIT, () -> service.addLineItem(
                submitter, created.submissionId(), line("electricity", date(2026, 7, 1), "1", "monthly")));
        assertMessage(HttpStatus.BAD_REQUEST, SubmissionService.ONLY_EDIT, () -> service.updateLineItem(
                submitter, created.submissionId(), lineId, line("revenue", date(2026, 7, 1), "11", "monthly")));
    }

    @Test
    void listIsNewestPeriodFirst() {
        service.create(submitter, request(date(2026, 1, 1), date(2026, 1, 31), "manual", null, 1, null, List.of()));
        SubmissionResponse later = service.create(submitter, request(
                date(2026, 6, 1), date(2026, 6, 30), "manual", null, 1, null, List.of()));

        List<SubmissionSummary> listed = service.list(decisionMaker);

        assertEquals(later.submissionId(), listed.get(0).submissionId());
        assertEquals(0, listed.get(0).lineItemCount());
        assertNull(listed.get(0).latestRejectionComment());
    }

    @Test
    void listKeepsOnlyTheLatestRejectionComment() {
        SubmissionResponse created = service.create(submitter, request(
                date(2026, 5, 1), date(2026, 5, 31), "manual", null, 1, null,
                List.of(line("revenue", date(2026, 5, 1), "10", "annual"))));
        service.submit(submitter, created.submissionId());
        service.reject(approver, created.submissionId(), "Fix the fuel line.");

        storedApprovals.add(new ApprovalAction(
                "older-note", created.submissionId(), APPROVER_ID,
                ApprovalAction.DECISION_REJECTED, "Older note", Instant.EPOCH));
        storedApprovals.add(new ApprovalAction(
                "approval-note", created.submissionId(), APPROVER_ID,
                ApprovalAction.DECISION_APPROVED, "Looks fine", Instant.parse("2026-12-01T00:00:00Z")));

        SubmissionResponse draft = service.create(submitter, request(
                date(2026, 4, 1), date(2026, 4, 30), "manual", null, 1, null, List.of()));

        SubmissionSummary rejected = null;
        SubmissionSummary stillDraft = null;
        for (SubmissionSummary row : service.list(approver)) {
            if (created.submissionId().equals(row.submissionId())) {
                rejected = row;
            }
            if (draft.submissionId().equals(row.submissionId())) {
                stillDraft = row;
            }
        }

        assertNotNull(rejected);
        assertEquals(BudgetSubmission.STATUS_REJECTED, rejected.status());
        assertEquals("Fix the fuel line.", rejected.latestRejectionComment());
        assertNotNull(stillDraft);
        assertNull(stillDraft.latestRejectionComment());
    }

    @Test
    void inactiveUserIsLoggedOut() {
        storedUsers.put(SUBMITTER_ID, user(SUBMITTER_ID, UNIT, "Sam Submitter", "submitter", "inactive"));

        ApiException ex = assertThrows(ApiException.class, () -> service.list(submitter));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(SubmissionService.LOGIN_AGAIN, ex.getMessage());
    }

    @Test
    void categoriesComeBackInSortOrder() {
        List<CategoryResponse> categories = service.listCategories(decisionMaker);

        assertEquals("revenue", categories.get(0).categoryCode());
        assertEquals("income_tax", categories.get(categories.size() - 1).categoryCode());
        assertTrue(categories.get(0).teamManaged());
    }

    private void assertMessage(HttpStatus status, String message, ThrowingCall call) {
        ApiException ex = assertThrows(ApiException.class, call::run);
        assertEquals(status, ex.getStatus());
        assertEquals(message, ex.getMessage());
    }

    private static SubmissionRequest request(LocalDate start, LocalDate end, String importMethod, String fileName,
                                             Integer scale, String replacesId, List<LineItemRequest> lines) {
        return new SubmissionRequest(start, end, importMethod, fileName, scale, replacesId, lines);
    }

    private static LineItemRequest line(String category, LocalDate period, String amount, String frequency) {
        return new LineItemRequest(category, period, new BigDecimal(amount), frequency);
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static BudgetSubmission submission(String id, String unitId, String userId, LocalDate start, LocalDate end,
                                               String status, Instant submittedAt, String replacesId) {
        return new BudgetSubmission(id, unitId, userId, start, end, status, submittedAt, replacesId, "manual", null, 1);
    }

    private static AppUser user(String id, String unitId, String name, String role) {
        return user(id, unitId, name, role, "active");
    }

    private static AppUser user(String id, String unitId, String name, String role, String status) {
        return new AppUser(id, unitId, name, name.toLowerCase().replace(' ', '.') + "@example.com", "hash", role, status);
    }

    private static List<LineCategory> sampleCategories() {
        Object[][] rows = {
                {"revenue", "Revenue", 1, true},
                {"other_income", "Other income", 2, false},
                {"cost_of_sales", "Cost of sales", 3, false},
                {"employee_costs", "Employee costs", 4, true},
                {"occupancy", "Occupancy", 5, false},
                {"electricity", "Electricity", 6, false},
                {"other_utilities", "Water, telecoms and other utilities", 7, false},
                {"transport_fuel", "Transport and fuel", 8, false},
                {"marketing", "Marketing", 9, false},
                {"other_operating", "Other operating expenses", 10, false},
                {"depreciation", "Depreciation and amortisation", 11, false},
                {"finance_costs", "Finance costs", 12, false},
                {"income_tax", "Income tax", 13, false}
        };
        List<LineCategory> categories = new ArrayList<>();
        for (Object[] row : rows) {
            categories.add(new LineCategory((String) row[0], (String) row[1], ((Integer) row[2]).shortValue(), (Boolean) row[3]));
        }
        categories.sort(Comparator.comparingInt(LineCategory::getSortOrder));
        return categories;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
