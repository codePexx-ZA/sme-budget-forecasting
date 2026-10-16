package com.smebudget.api.submissions;

import com.smebudget.api.auth.AuthenticatedUser;
import com.smebudget.api.common.MessageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Budget submissions (Aiden). Every call needs a login token. Roles are checked again in
 * {@link SubmissionService}; these annotations are the HTTP gate.
 *
 * <pre>
 * GET    /api/submissions/categories                         any logged-in user
 * GET    /api/submissions                                    any logged-in user, own business unit
 * GET    /api/submissions/{id}                               any logged-in user, own business unit
 * POST   /api/submissions                                    submitter, creates a draft
 * PUT    /api/submissions/{id}                               submitter, draft or rejected
 * DELETE /api/submissions/{id}                               submitter, draft or rejected
 * POST   /api/submissions/{id}/line-items                    submitter, draft or rejected
 * PUT    /api/submissions/{id}/line-items/{lineItemId}       submitter, draft or rejected
 * DELETE /api/submissions/{id}/line-items/{lineItemId}       submitter, draft or rejected
 * POST   /api/submissions/{id}/submit                        submitter
 * POST   /api/submissions/{id}/approve                       approver
 * POST   /api/submissions/{id}/reject                        approver, comment required
 * </pre>
 */
@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissions;

    public SubmissionController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories(@AuthenticationPrincipal AuthenticatedUser user) {
        return submissions.listCategories(user);
    }

    @GetMapping
    public List<SubmissionSummary> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return submissions.list(user);
    }

    @GetMapping("/{id}")
    public SubmissionResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return submissions.get(user, id);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUBMITTER')")
    public SubmissionResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody SubmissionRequest request) {
        return submissions.create(user, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUBMITTER')")
    public SubmissionResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable String id,
                                     @Valid @RequestBody SubmissionRequest request) {
        return submissions.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUBMITTER')")
    public MessageResponse delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        submissions.delete(user, id);
        return new MessageResponse("Budget submission deleted.");
    }

    @PostMapping("/{id}/line-items")
    @PreAuthorize("hasRole('SUBMITTER')")
    public LineItemResponse addLine(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable String id,
                                    @Valid @RequestBody LineItemRequest request) {
        return submissions.addLineItem(user, id, request);
    }

    @PutMapping("/{id}/line-items/{lineItemId}")
    @PreAuthorize("hasRole('SUBMITTER')")
    public LineItemResponse updateLine(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable String id,
                                       @PathVariable String lineItemId,
                                       @Valid @RequestBody LineItemRequest request) {
        return submissions.updateLineItem(user, id, lineItemId, request);
    }

    @DeleteMapping("/{id}/line-items/{lineItemId}")
    @PreAuthorize("hasRole('SUBMITTER')")
    public MessageResponse deleteLine(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable String id,
                                      @PathVariable String lineItemId) {
        submissions.deleteLineItem(user, id, lineItemId);
        return new MessageResponse("Line item deleted.");
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('SUBMITTER')")
    public SubmissionResponse submit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return submissions.submit(user, id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    public SubmissionResponse approve(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable String id,
                                      @Valid @RequestBody(required = false) ApproveRequest request) {
        return submissions.approve(user, id, request == null ? null : request.comments());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('APPROVER')")
    public SubmissionResponse reject(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable String id,
                                     @Valid @RequestBody RejectRequest request) {
        return submissions.reject(user, id, request.comments());
    }
}
