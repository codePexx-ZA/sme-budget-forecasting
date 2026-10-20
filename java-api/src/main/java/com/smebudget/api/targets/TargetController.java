package com.smebudget.api.targets;

import com.smebudget.api.auth.AuthenticatedUser;
import com.smebudget.api.common.MessageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * KPI targets (Boitu). Every call needs a login token. Writes are limited to the decision maker.
 *
 * <pre>
 * GET    /api/targets        any logged-in user, own business unit
 * GET    /api/targets/{id}   any logged-in user, own business unit
 * POST   /api/targets        decision maker
 * PUT    /api/targets/{id}   decision maker
 * DELETE /api/targets/{id}   decision maker
 * </pre>
 */
@RestController
@RequestMapping("/api/targets")
public class TargetController {

    private final TargetService targets;

    public TargetController(TargetService targets) {
        this.targets = targets;
    }

    @GetMapping
    public List<KpiTargetResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return targets.list(user);
    }

    @GetMapping("/{id}")
    public KpiTargetResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return targets.get(user, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DECISION_MAKER')")
    public KpiTargetResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                    @Valid @RequestBody KpiTargetRequest request) {
        return targets.create(user, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DECISION_MAKER')")
    public KpiTargetResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable String id,
                                    @Valid @RequestBody KpiTargetRequest request) {
        return targets.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DECISION_MAKER')")
    public MessageResponse delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        targets.delete(user, id);
        return new MessageResponse("KPI target deleted.");
    }
}
