package com.smebudget.api.targets;

import com.smebudget.api.auth.AppUser;
import com.smebudget.api.auth.AppUserRepository;
import com.smebudget.api.auth.AuthenticatedUser;
import com.smebudget.api.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KPI targets (Boitu): create, view, edit and delete a target for the logged-in user's business unit.
 * Only a decision maker may change targets. Anyone logged in may read the targets for their own unit.
 */
@Service
public class TargetService {

    static final String LOGIN_AGAIN = "Please log in to continue.";
    static final String FORBIDDEN = "Your role does not have access to this.";
    static final String NOT_FOUND = "KPI target not found.";
    static final String DUPLICATE = "A target with this name already exists for that period.";
    static final String DECISION_MAKER = "decision_maker";

    private final KpiTargetRepository targets;
    private final AppUserRepository users;

    public TargetService(KpiTargetRepository targets, AppUserRepository users) {
        this.targets = targets;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<KpiTargetResponse> list(AuthenticatedUser principal) {
        AppUser user = requireUser(principal);
        return targets.findByUnitIdOrderByTargetPeriodDescMetricNameAsc(user.getUnitId()).stream()
                .map(KpiTargetResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public KpiTargetResponse get(AuthenticatedUser principal, String id) {
        return KpiTargetResponse.from(requireInUnit(id, requireUser(principal).getUnitId()));
    }

    @Transactional
    public KpiTargetResponse create(AuthenticatedUser principal, KpiTargetRequest request) {
        AppUser user = requireDecisionMaker(principal);
        Draft draft = normalize(request);
        requireUnique(user.getUnitId(), draft, null);
        KpiTarget saved = targets.save(new KpiTarget(
                newId(), user.getUnitId(), draft.metricName(), draft.value(), draft.unit(), draft.period()));
        return KpiTargetResponse.from(saved);
    }

    @Transactional
    public KpiTargetResponse update(AuthenticatedUser principal, String id, KpiTargetRequest request) {
        AppUser user = requireDecisionMaker(principal);
        KpiTarget existing = requireInUnit(id, user.getUnitId());
        Draft draft = normalize(request);
        requireUnique(user.getUnitId(), draft, existing.getTargetId());
        existing.update(draft.metricName(), draft.value(), draft.unit(), draft.period());
        return KpiTargetResponse.from(targets.save(existing));
    }

    @Transactional
    public void delete(AuthenticatedUser principal, String id) {
        AppUser user = requireDecisionMaker(principal);
        targets.delete(requireInUnit(id, user.getUnitId()));
    }

    /** The database allows one row per unit, metric name and period. */
    private void requireUnique(String unitId, Draft draft, String ignoreId) {
        targets.findByUnitIdAndMetricNameAndTargetPeriod(unitId, draft.metricName(), draft.period())
                .filter(other -> !other.getTargetId().equals(ignoreId))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT, DUPLICATE);
                });
    }

    private KpiTarget requireInUnit(String id, String unitId) {
        KpiTarget target = targets.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (!unitId.equals(target.getUnitId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return target;
    }

    private AppUser requireDecisionMaker(AuthenticatedUser principal) {
        AppUser user = requireUser(principal);
        if (!DECISION_MAKER.equals(principal.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN);
        }
        return user;
    }

    private AppUser requireUser(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, LOGIN_AGAIN);
        }
        return users.findById(principal.userId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, LOGIN_AGAIN));
    }

    private static Draft normalize(KpiTargetRequest request) {
        String name = request.metricName() == null ? "" : request.metricName().trim();
        String unit = request.measurementUnit() == null ? "" : request.measurementUnit().trim();
        if (name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Metric name is required.");
        }
        if (name.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Metric name must be 100 characters or fewer.");
        }
        if (unit.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Measurement unit is required.");
        }
        if (unit.length() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Measurement unit must be 50 characters or fewer.");
        }
        if (request.targetValue() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Target value is required.");
        }
        if (request.targetPeriod() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Target period is required.");
        }
        BigDecimal value = request.targetValue().setScale(2, RoundingMode.HALF_UP);
        return new Draft(name, value, unit, request.targetPeriod());
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record Draft(String metricName, BigDecimal value, String unit, LocalDate period) {
    }
}
