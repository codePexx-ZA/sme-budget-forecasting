package com.smebudget.api.submissions;

import com.smebudget.api.auth.AppUser;
import com.smebudget.api.auth.AppUserRepository;
import com.smebudget.api.auth.AuthenticatedUser;
import com.smebudget.api.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget submissions (Aiden): create, view, edit and delete a submission and its line items,
 * then submit, approve or reject it.
 *
 * <p>Amounts are stored in rands. A draft or a rejected submission can still be changed.
 * Submitting it sends it to an approver. Approving a submission that replaces an approved one
 * marks the older one superseded; it is kept for history and is not deleted. Forecasts read
 * only approved submissions (that rule lives in the forecasting service).
 */
@Service
public class SubmissionService {

    static final String LOGIN_AGAIN = "Please log in to continue.";
    static final String FORBIDDEN = "Your role does not have access to this.";
    static final String NOT_FOUND = "Submission not found.";
    static final String LINE_NOT_FOUND = "Line item not found.";
    static final String PERIOD_START_REQUIRED = "Period start is required.";
    static final String PERIOD_END_REQUIRED = "Period end is required.";
    static final String PERIOD_ORDER = "Period end must be on or after period start.";
    static final String LINE_CATEGORY_REQUIRED = "Each line needs a category.";
    static final String LINE_PERIOD_REQUIRED = "Each line needs a period.";
    static final String AMOUNT_REQUIRED = "Each line needs an amount.";
    static final String BAD_AMOUNT_SCALE = "Amount can have at most 2 decimal places.";
    static final String AMOUNT_TOO_LARGE = "Amount is too large.";
    static final String UNKNOWN_CATEGORY = "Unknown category. Choose one from the category list.";
    static final String BAD_FREQUENCY = "Frequency must be monthly, quarterly or annual.";
    static final String BAD_IMPORT = "Import method must be manual, template or mapped_import.";
    static final String BAD_SCALE = "Source scale must be 1, 1000 or 1000000.";
    static final String FILE_NAME_LONG = "File name must be 255 characters or fewer.";
    static final String COMMENTS_LONG = "Comments must be 2000 characters or fewer.";
    static final String DUPLICATE_LINE = "A category can only appear once for the same period.";
    static final String LINE_OUTSIDE = "Each line's period must fall inside the submission period.";
    static final String ONLY_EDIT = "Only a draft or a rejected submission can be changed.";
    static final String ONLY_DELETE = "Only a draft or a rejected submission can be deleted.";
    static final String KEPT_BECAUSE_REPLACED = "Another submission replaces this one, so it has to be kept.";
    static final String ONLY_SUBMIT = "Only a draft or a rejected submission can be submitted.";
    static final String NEED_A_LINE = "Add at least one line item before submitting.";
    static final String ONLY_APPROVE = "Only a submission that is waiting for approval can be approved.";
    static final String ONLY_REJECT = "Only a submission that is waiting for approval can be rejected.";
    static final String REJECTION_NEEDS_COMMENT = "A rejection must include a comment.";
    static final String SELF_REPLACE = "A submission cannot replace itself.";
    static final String REPLACE_MISSING = "The submission being replaced was not found.";
    static final String REPLACE_OTHER_UNIT = "You can only replace a submission from your own business unit.";
    static final String REPLACE_LOOP = "That replacement would create a loop.";
    static final String LINE_MISSING = "A line item is missing.";

    private static final String ROLE_SUBMITTER = "submitter";
    private static final String ROLE_APPROVER = "approver";

    private final BudgetSubmissionRepository submissions;
    private final FinancialLineItemRepository lineItems;
    private final ApprovalActionRepository approvals;
    private final LineCategoryRepository categories;
    private final AppUserRepository users;

    public SubmissionService(BudgetSubmissionRepository submissions,
                             FinancialLineItemRepository lineItems,
                             ApprovalActionRepository approvals,
                             LineCategoryRepository categories,
                             AppUserRepository users) {
        this.submissions = submissions;
        this.lineItems = lineItems;
        this.approvals = approvals;
        this.categories = categories;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(AuthenticatedUser principal) {
        requireUser(principal);
        List<CategoryResponse> result = new ArrayList<>();
        for (LineCategory category : categories.findAllByOrderBySortOrderAsc()) {
            result.add(new CategoryResponse(
                    category.getCategoryCode(), category.getDisplayName(), category.getSortOrder(), category.isTeamManaged()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SubmissionSummary> list(AuthenticatedUser principal) {
        AppUser user = requireUser(principal);
        List<BudgetSubmission> rows = new ArrayList<>(submissions.findByUnitId(user.getUnitId()));
        rows.sort(Comparator.comparing(BudgetSubmission::getPeriodStart, Comparator.reverseOrder())
                .thenComparing(BudgetSubmission::getSubmissionId, Comparator.reverseOrder()));
        List<SubmissionSummary> result = new ArrayList<>();
        for (BudgetSubmission row : rows) {
            result.add(toSummary(row));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SubmissionResponse get(AuthenticatedUser principal, String id) {
        AppUser user = requireUser(principal);
        return toResponse(requireInUnit(id, user.getUnitId()));
    }

    /** Saves a new draft for the submitter's business unit. */
    @Transactional
    public SubmissionResponse create(AuthenticatedUser principal, SubmissionRequest request) {
        AppUser user = requireUser(principal);
        requireRole(principal, ROLE_SUBMITTER);
        Map<String, LineCategory> categoryMap = categoryMap();
        Header header = normalizeHeader(request);
        String id = Ids.newId();
        header = header.withReplaces(checkReplaces(user.getUnitId(), id, header.replacesSubmissionId()));
        List<LineDraft> lines = normalizeLines(header, request.lineItems() == null ? List.of() : request.lineItems(), categoryMap);

        BudgetSubmission submission = new BudgetSubmission(
                id, user.getUnitId(), user.getUserId(),
                header.periodStart(), header.periodEnd(),
                BudgetSubmission.STATUS_DRAFT, null,
                header.replacesSubmissionId(), header.importMethod(), header.sourceFileName(), header.sourceScale());
        submissions.save(submission);
        replaceLines(id, lines);
        return toResponse(submission);
    }

    /**
     * Updates a draft, or a rejected submission (which goes back to draft so it can be submitted again).
     * Passing lineItems replaces the whole set. Leaving lineItems out keeps the current lines.
     */
    @Transactional
    public SubmissionResponse update(AuthenticatedUser principal, String id, SubmissionRequest request) {
        AppUser user = requireUser(principal);
        requireRole(principal, ROLE_SUBMITTER);
        BudgetSubmission submission = requireEditable(id, user.getUnitId());
        Map<String, LineCategory> categoryMap = categoryMap();
        Header header = normalizeHeader(request);
        header = header.withReplaces(checkReplaces(user.getUnitId(), submission.getSubmissionId(), header.replacesSubmissionId()));
        List<LineDraft> lines = request.lineItems() == null
                ? null
                : normalizeLines(header, request.lineItems(), categoryMap);
        if (lines == null) {
            // The lines stay as they are, so they still have to sit inside the new period.
            for (FinancialLineItem existing : lineItems.findBySubmissionId(submission.getSubmissionId())) {
                if (existing.getPeriodStart().isBefore(header.periodStart()) || existing.getPeriodStart().isAfter(header.periodEnd())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, LINE_OUTSIDE);
                }
            }
        }

        applyHeader(submission, header);
        reopenIfRejected(submission);
        submissions.save(submission);
        if (lines != null) {
            replaceLines(submission.getSubmissionId(), lines);
        }
        return toResponse(submission);
    }

    @Transactional
    public void delete(AuthenticatedUser principal, String id) {
        AppUser user = requireUser(principal);
        requireRole(principal, ROLE_SUBMITTER);
        BudgetSubmission submission = requireInUnit(id, user.getUnitId());
        if (!BudgetSubmission.EDITABLE_STATUSES.contains(submission.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ONLY_DELETE);
        }
        // replaces_submission_id is ON DELETE RESTRICT, so a newer version keeps this row for history.
        if (submissions.existsByReplacesSubmissionId(submission.getSubmissionId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, KEPT_BECAUSE_REPLACED);
        }
        lineItems.deleteAll(lineItems.findBySubmissionId(submission.getSubmissionId()));
        approvals.deleteAll(approvals.findBySubmissionId(submission.getSubmissionId()));
        submissions.delete(submission);
    }

    @Transactional
    public LineItemResponse addLineItem(AuthenticatedUser principal, String id, LineItemRequest request) {
        AppUser user = requireUser(principal);
        requireRole(principal, ROLE_SUBMITTER);
        BudgetSubmission submission = requireEditable(id, user.getUnitId());
        LineDraft draft = normalizeLine(request, categoryMap());
        requireLineInside(submission, draft.periodStart());
        requireUnique(submission.getSubmissionId(), draft, null);
        reopenIfRejected(submission);
        submissions.save(submission);
        FinancialLineItem saved = lineItems.save(new FinancialLineItem(
                Ids.newId(), submission.getSubmissionId(), draft.category(), draft.periodStart(), draft.amount(), draft.frequency()));
        return toLine(saved, categoryMap());
    }

    @Transactional
    public LineItemResponse updateLineItem(AuthenticatedUser principal, String id, String lineItemId, LineItemRequest request) {
        AppUser user = requireUser(principal);
        requireRole(principal, ROLE_SUBMITTER);
        BudgetSubmission submission = requireEditable(id, user.getUnitId());
        FinancialLineItem line = requireLine(submission.getSubmissionId(), lineItemId);
        LineDraft draft = normalizeLine(request, categoryMap());
        requireLineInside(submission, draft.periodStart());
        requireUnique(submission.getSubmissionId(), draft, line.getLineItemId());
        line.setCategory(draft.category());
        line.setPeriodStart(draft.periodStart());
        line.setAmount(draft.amount());
        line.setFrequency(draft.frequency());
        reopenIfRejected(submission);
        submissions.save(submission);
        return toLine(lineItems.save(line), categoryMap());
    }

    @Transactional
    public void deleteLineItem(AuthenticatedUser principal, String id, String lineItemId) {
        AppUser user = requireUser(principal);
        requireRole(principal, ROLE_SUBMITTER);
        BudgetSubmission submission = requireEditable(id, user.getUnitId());
        FinancialLineItem line = requireLine(submission.getSubmissionId(), lineItemId);
        lineItems.delete(line);
        reopenIfRejected(submission);
        submissions.save(submission);
    }

    /** Moves a draft or rejected submission to submitted. The database requires submitted_at for that status. */
    @Transactional
    public SubmissionResponse submit(AuthenticatedUser principal, String id) {
        AppUser user = requireUser(principal);
        requireRole(principal, ROLE_SUBMITTER);
        BudgetSubmission submission = requireInUnit(id, user.getUnitId());
        if (!BudgetSubmission.EDITABLE_STATUSES.contains(submission.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ONLY_SUBMIT);
        }
        if (lineItems.findBySubmissionId(submission.getSubmissionId()).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, NEED_A_LINE);
        }
        submission.setStatus(BudgetSubmission.STATUS_SUBMITTED);
        submission.setSubmittedAt(Instant.now());
        submissions.save(submission);
        return toResponse(submission);
    }

    /**
     * Approves a submitted budget and stores the decision. If this submission replaces an approved
     * one, that older one becomes superseded and stays in the database for history.
     */
    @Transactional
    public SubmissionResponse approve(AuthenticatedUser principal, String id, String comments) {
        AppUser user = requireUser(principal);
        requireRole(principal, ROLE_APPROVER);
        BudgetSubmission submission = requireInUnit(id, user.getUnitId());
        if (!BudgetSubmission.STATUS_SUBMITTED.equals(submission.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ONLY_APPROVE);
        }
        String note = cleanComments(comments, false);
        submission.setStatus(BudgetSubmission.STATUS_APPROVED);
        submissions.save(submission);
        approvals.save(new ApprovalAction(
                Ids.newId(), submission.getSubmissionId(), user.getUserId(),
                ApprovalAction.DECISION_APPROVED, note, Instant.now()));
        supersedePrevious(submission);
        return toResponse(submission);
    }

    /** Rejects a submitted budget. Comments are required so the submitter knows what to fix. */
    @Transactional
    public SubmissionResponse reject(AuthenticatedUser principal, String id, String comments) {
        AppUser user = requireUser(principal);
        requireRole(principal, ROLE_APPROVER);
        BudgetSubmission submission = requireInUnit(id, user.getUnitId());
        if (!BudgetSubmission.STATUS_SUBMITTED.equals(submission.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ONLY_REJECT);
        }
        String note = cleanComments(comments, true);
        submission.setStatus(BudgetSubmission.STATUS_REJECTED);
        submissions.save(submission);
        approvals.save(new ApprovalAction(
                Ids.newId(), submission.getSubmissionId(), user.getUserId(),
                ApprovalAction.DECISION_REJECTED, note, Instant.now()));
        return toResponse(submission);
    }

    /** Editing a rejected budget returns it to draft and clears submitted_at, which only drafts may omit. */
    private static void reopenIfRejected(BudgetSubmission submission) {
        if (BudgetSubmission.STATUS_REJECTED.equals(submission.getStatus())) {
            submission.setStatus(BudgetSubmission.STATUS_DRAFT);
            submission.setSubmittedAt(null);
        }
    }

    private void supersedePrevious(BudgetSubmission submission) {
        String previousId = submission.getReplacesSubmissionId();
        if (previousId == null) {
            return;
        }
        BudgetSubmission previous = submissions.findById(previousId).orElse(null);
        if (previous != null && BudgetSubmission.STATUS_APPROVED.equals(previous.getStatus())) {
            previous.setStatus(BudgetSubmission.STATUS_SUPERSEDED);
            submissions.save(previous);
        }
    }

    private BudgetSubmission requireEditable(String id, String unitId) {
        BudgetSubmission submission = requireInUnit(id, unitId);
        if (!BudgetSubmission.EDITABLE_STATUSES.contains(submission.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ONLY_EDIT);
        }
        return submission;
    }

    private BudgetSubmission requireInUnit(String id, String unitId) {
        BudgetSubmission submission = submissions.findById(id == null ? "" : id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (!unitId.equals(submission.getUnitId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return submission;
    }

    private FinancialLineItem requireLine(String submissionId, String lineItemId) {
        return lineItems.findById(lineItemId == null ? "" : lineItemId)
                .filter(line -> submissionId.equals(line.getSubmissionId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LINE_NOT_FOUND));
    }

    private void requireUnique(String submissionId, LineDraft draft, String ignoreLineId) {
        for (FinancialLineItem existing : lineItems.findBySubmissionId(submissionId)) {
            if (existing.getLineItemId().equals(ignoreLineId)) {
                continue;
            }
            if (existing.getCategory().equals(draft.category()) && existing.getPeriodStart().equals(draft.periodStart())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, DUPLICATE_LINE);
            }
        }
    }

    private static void requireLineInside(BudgetSubmission submission, LocalDate linePeriod) {
        if (linePeriod.isBefore(submission.getPeriodStart()) || linePeriod.isAfter(submission.getPeriodEnd())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, LINE_OUTSIDE);
        }
    }

    private AppUser requireUser(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, LOGIN_AGAIN);
        }
        return users.findById(principal.userId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, LOGIN_AGAIN));
    }

    private static void requireRole(AuthenticatedUser principal, String role) {
        if (principal == null || !role.equals(principal.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN);
        }
    }

    private Map<String, LineCategory> categoryMap() {
        Map<String, LineCategory> map = new LinkedHashMap<>();
        for (LineCategory category : categories.findAllByOrderBySortOrderAsc()) {
            map.put(category.getCategoryCode(), category);
        }
        return map;
    }

    private Header normalizeHeader(SubmissionRequest request) {
        if (request.periodStart() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PERIOD_START_REQUIRED);
        }
        if (request.periodEnd() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PERIOD_END_REQUIRED);
        }
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PERIOD_ORDER);
        }
        String importMethod = request.importMethod() == null || request.importMethod().isBlank()
                ? BudgetSubmission.IMPORT_MANUAL
                : request.importMethod().trim().toLowerCase(Locale.ROOT);
        if (!BudgetSubmission.IMPORT_METHODS.contains(importMethod)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_IMPORT);
        }
        int sourceScale = request.sourceScale() == null ? 1 : request.sourceScale();
        if (!BudgetSubmission.SOURCE_SCALES.contains(sourceScale)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_SCALE);
        }
        String fileName = blankToNull(request.sourceFileName());
        if (fileName != null && fileName.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FILE_NAME_LONG);
        }
        return new Header(request.periodStart(), request.periodEnd(), importMethod, fileName, sourceScale,
                blankToNull(request.replacesSubmissionId()));
    }

    /**
     * Walks replaces_submission_id so a submission cannot point at itself or at a chain that comes back to it.
     * Returns the id to store, or null when the field was left blank.
     */
    private String checkReplaces(String unitId, String selfId, String replacesId) {
        if (replacesId == null) {
            return null;
        }
        if (replacesId.equals(selfId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, SELF_REPLACE);
        }
        String cursor = replacesId;
        Set<String> seen = new HashSet<>();
        for (int step = 0; step < 20 && cursor != null; step++) {
            if (!seen.add(cursor) || cursor.equals(selfId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, REPLACE_LOOP);
            }
            BudgetSubmission other = submissions.findById(cursor)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, REPLACE_MISSING));
            if (!unitId.equals(other.getUnitId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, REPLACE_OTHER_UNIT);
            }
            cursor = other.getReplacesSubmissionId();
        }
        if (cursor != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, REPLACE_LOOP);
        }
        return replacesId;
    }

    private List<LineDraft> normalizeLines(Header header, List<LineItemRequest> requests, Map<String, LineCategory> categoryMap) {
        List<LineDraft> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LineItemRequest request : requests) {
            if (request == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, LINE_MISSING);
            }
            LineDraft draft = normalizeLine(request, categoryMap);
            if (draft.periodStart().isBefore(header.periodStart()) || draft.periodStart().isAfter(header.periodEnd())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, LINE_OUTSIDE);
            }
            if (!seen.add(draft.category() + "|" + draft.periodStart())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, DUPLICATE_LINE);
            }
            lines.add(draft);
        }
        return lines;
    }

    private LineDraft normalizeLine(LineItemRequest request, Map<String, LineCategory> categoryMap) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, LINE_MISSING);
        }
        String category = request.category() == null ? "" : request.category().trim();
        if (category.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, LINE_CATEGORY_REQUIRED);
        }
        if (!categoryMap.containsKey(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, UNKNOWN_CATEGORY);
        }
        if (request.periodStart() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, LINE_PERIOD_REQUIRED);
        }
        String frequency = request.frequency() == null || request.frequency().isBlank()
                ? FinancialLineItem.ANNUAL
                : request.frequency().trim().toLowerCase(Locale.ROOT);
        if (!FinancialLineItem.FREQUENCIES.contains(frequency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_FREQUENCY);
        }
        return new LineDraft(category, request.periodStart(), normalizeAmount(request.amount()), frequency);
    }

    /** Money is NUMERIC(18,2): at most 16 digits before the decimal point and 2 after it. */
    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, AMOUNT_REQUIRED);
        }
        BigDecimal plain = amount.stripTrailingZeros();
        if (plain.scale() < 0) {
            plain = plain.setScale(0);
        }
        if (plain.precision() - plain.scale() > 16) {
            throw new ApiException(HttpStatus.BAD_REQUEST, AMOUNT_TOO_LARGE);
        }
        if (plain.scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_AMOUNT_SCALE);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String cleanComments(String comments, boolean required) {
        String note = blankToNull(comments);
        if (required && note == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, REJECTION_NEEDS_COMMENT);
        }
        if (note != null && note.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, COMMENTS_LONG);
        }
        return note;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void applyHeader(BudgetSubmission submission, Header header) {
        submission.setPeriodStart(header.periodStart());
        submission.setPeriodEnd(header.periodEnd());
        submission.setImportMethod(header.importMethod());
        submission.setSourceFileName(header.sourceFileName());
        submission.setSourceScale(header.sourceScale());
        submission.setReplacesSubmissionId(header.replacesSubmissionId());
    }

    /**
     * Replaces every line of a submission. The delete is flushed first so the unique
     * (submission, category, period) rows are free before the new ones are inserted.
     */
    private void replaceLines(String submissionId, List<LineDraft> drafts) {
        List<FinancialLineItem> existing = lineItems.findBySubmissionId(submissionId);
        if (!existing.isEmpty()) {
            lineItems.deleteAll(existing);
            lineItems.flush();
        }
        for (LineDraft draft : drafts) {
            lineItems.save(new FinancialLineItem(
                    Ids.newId(), submissionId, draft.category(), draft.periodStart(), draft.amount(), draft.frequency()));
        }
    }

    private SubmissionSummary toSummary(BudgetSubmission submission) {
        return new SubmissionSummary(
                submission.getSubmissionId(),
                submission.getSubmittedBy(),
                userName(submission.getSubmittedBy()),
                submission.getPeriodStart(),
                submission.getPeriodEnd(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getImportMethod(),
                submission.getSourceScale(),
                lineItems.findBySubmissionId(submission.getSubmissionId()).size(),
                latestRejectionComment(submission));
    }

    /**
     * The comment the approver wrote last time they sent this budget back.
     * Empty for every status except rejected, so an old note does not stay on a budget
     * that has since been submitted again.
     */
    private String latestRejectionComment(BudgetSubmission submission) {
        if (!BudgetSubmission.STATUS_REJECTED.equals(submission.getStatus())) {
            return null;
        }
        ApprovalAction latest = null;
        for (ApprovalAction action : approvals.findBySubmissionId(submission.getSubmissionId())) {
            if (!ApprovalAction.DECISION_REJECTED.equals(action.getDecision())) {
                continue;
            }
            if (latest == null || isAfter(action.getActedAt(), latest.getActedAt())) {
                latest = action;
            }
        }
        if (latest == null || latest.getComments() == null || latest.getComments().isBlank()) {
            return null;
        }
        return latest.getComments().trim();
    }

    private static boolean isAfter(Instant candidate, Instant current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        return candidate.isAfter(current);
    }

    private SubmissionResponse toResponse(BudgetSubmission submission) {
        Map<String, LineCategory> categoryMap = categoryMap();
        List<FinancialLineItem> lines = new ArrayList<>(lineItems.findBySubmissionId(submission.getSubmissionId()));
        lines.sort(Comparator.comparing(FinancialLineItem::getPeriodStart)
                .thenComparingInt(line -> sortOrder(categoryMap, line.getCategory())));

        List<LineItemResponse> lineResponses = new ArrayList<>();
        for (FinancialLineItem line : lines) {
            lineResponses.add(toLine(line, categoryMap));
        }

        List<ApprovalAction> actions = new ArrayList<>(approvals.findBySubmissionId(submission.getSubmissionId()));
        actions.sort(Comparator.comparing(ApprovalAction::getActedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<ApprovalResponse> approvalResponses = new ArrayList<>();
        for (ApprovalAction action : actions) {
            approvalResponses.add(new ApprovalResponse(
                    action.getApprovalId(), action.getActedBy(), userName(action.getActedBy()),
                    action.getDecision(), action.getComments(), action.getActedAt()));
        }

        return new SubmissionResponse(
                submission.getSubmissionId(),
                submission.getUnitId(),
                submission.getSubmittedBy(),
                userName(submission.getSubmittedBy()),
                submission.getPeriodStart(),
                submission.getPeriodEnd(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getReplacesSubmissionId(),
                replacesLabel(submission.getReplacesSubmissionId()),
                submission.getImportMethod(),
                submission.getSourceFileName(),
                submission.getSourceScale(),
                lineResponses,
                approvalResponses,
                StatementTotals.calculate(lines));
    }

    private LineItemResponse toLine(FinancialLineItem line, Map<String, LineCategory> categoryMap) {
        LineCategory category = categoryMap.get(line.getCategory());
        String name = category == null ? line.getCategory() : category.getDisplayName();
        return new LineItemResponse(
                line.getLineItemId(), line.getCategory(), name, line.getPeriodStart(), line.getAmount(), line.getFrequency());
    }

    private static int sortOrder(Map<String, LineCategory> categoryMap, String code) {
        LineCategory category = categoryMap.get(code);
        return category == null ? Integer.MAX_VALUE : category.getSortOrder();
    }

    private String replacesLabel(String replacesId) {
        if (replacesId == null) {
            return null;
        }
        return submissions.findById(replacesId)
                .map(other -> other.getPeriodStart() + " to " + other.getPeriodEnd() + " (" + other.getStatus() + ")")
                .orElse(replacesId);
    }

    private String userName(String userId) {
        return users.findById(userId).map(AppUser::getFullName).orElse("Unknown user");
    }

    private record Header(LocalDate periodStart, LocalDate periodEnd, String importMethod,
                          String sourceFileName, int sourceScale, String replacesSubmissionId) {

        Header withReplaces(String replacesSubmissionId) {
            return new Header(periodStart, periodEnd, importMethod, sourceFileName, sourceScale, replacesSubmissionId);
        }
    }

    private record LineDraft(String category, LocalDate periodStart, BigDecimal amount, String frequency) {
    }
}
