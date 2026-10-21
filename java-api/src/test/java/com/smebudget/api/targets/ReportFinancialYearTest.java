package com.smebudget.api.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smebudget.api.auth.AppUser;
import com.smebudget.api.auth.AppUserRepository;
import com.smebudget.api.auth.AuthenticatedUser;
import com.smebudget.api.submissions.ApprovalActionRepository;
import com.smebudget.api.submissions.BudgetSubmissionRepository;
import com.smebudget.api.submissions.FinancialLineItemRepository;
import com.smebudget.api.submissions.LineCategoryRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The financial-year chips read year_end_month from the business row the report already loads. */
class ReportFinancialYearTest {

    @Test
    void februaryYearEndComesFromTheBusinessRow() {
        FinancialYearResponse year = readYearEnd(2);

        assertEquals(2, year.yearEndMonth());
        FinancialYear.Span current = FinancialYear.containing(LocalDate.now(ReportService.JOHANNESBURG), 2);
        assertEquals(current.start(), year.thisStart());
        assertEquals(current.end(), year.thisEnd());
        assertEquals(FinancialYear.following(current).start(), year.nextStart());
        assertEquals(FinancialYear.following(current).end(), year.nextEnd());
    }

    @Test
    void aMissingBusinessRowStaysOnFebruary() {
        FinancialYearResponse year = readYearEnd(null);

        assertEquals(FinancialYear.DEFAULT_YEAR_END_MONTH, year.yearEndMonth());
        assertEquals(3, year.thisStart().getMonthValue());
        assertEquals(1, year.thisStart().getDayOfMonth());
        assertEquals(2, year.thisEnd().getMonthValue());
    }

    private static FinancialYearResponse readYearEnd(Integer yearEndMonth) {
        AppUserRepository users = mock(AppUserRepository.class);
        ReportUnitRepository units = mock(ReportUnitRepository.class);
        ReportBusinessRepository businesses = mock(ReportBusinessRepository.class);
        AppUser user = new AppUser("user", "unit", "Sam Submitter", "sam@example.com", "hash", "submitter", "active");
        when(users.findById("user")).thenReturn(Optional.of(user));

        if (yearEndMonth == null) {
            when(units.findById("unit")).thenReturn(Optional.empty());
        } else {
            ReportUnit unit = mock(ReportUnit.class);
            ReportBusiness business = mock(ReportBusiness.class);
            when(unit.getBusinessId()).thenReturn("business");
            when(business.getYearEndMonth()).thenReturn(yearEndMonth);
            when(units.findById("unit")).thenReturn(Optional.of(unit));
            when(businesses.findById("business")).thenReturn(Optional.of(business));
        }

        ReportService reports = new ReportService(
                users, units, businesses,
                mock(BudgetSubmissionRepository.class),
                mock(FinancialLineItemRepository.class),
                mock(ApprovalActionRepository.class),
                mock(LineCategoryRepository.class),
                mock(KpiTargetRepository.class),
                mock(SavedForecastRunRepository.class),
                mock(SavedForecastValueRepository.class),
                mock(SavedAlertRepository.class));
        return reports.financialYear(new AuthenticatedUser("user", "submitter", "token"));
    }
}
