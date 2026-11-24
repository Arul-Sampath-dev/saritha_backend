package com.saritha.fashions.modules.report.service;

import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.Role;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.report.repository.ReportRepository;
import com.saritha.fashions.util.AuthUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.support.ServletRequestHandledEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The short report cache: repeated reads hit MongoDB once; a successful change request empties it. */
class ReportCacheTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 23);

    private ReportRepository repository;
    private ReportReadServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(ReportRepository.class);
        AuthUtil authUtil = mock(AuthUtil.class);
        User owner = new User();
        owner.setUserId("u1");
        owner.setRoles(Set.of(new Role(AppRole.SHOP_OWNER)));
        when(authUtil.loggedInUser()).thenReturn(owner);
        when(repository.findShopIdOfUser("u1")).thenReturn(Optional.of("shop-1"));
        when(repository.findShops(any())).thenReturn(List.of());
        service = new ReportReadServiceImpl(repository, authUtil);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner", "x", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void repeatedReadsAreServedFromTheCache() {
        service.getFinancialSummary(null, FROM, TO);
        service.getFinancialSummary(null, FROM, TO);
        service.getFinancialSummary(null, FROM, TO);
        verify(repository, times(1)).salesByShop(eq("shop-1"), any(), any());

        service.getFinancialSummary(null, FROM, TO.minusDays(1)); // another period: computed
        verify(repository, times(2)).salesByShop(eq("shop-1"), any(), any());
    }

    @Test
    void aSuccessfulChangeEmptiesTheCacheButAReadOrAFailureDoesNot() {
        service.getFinancialSummary(null, FROM, TO);

        service.onRequestHandled(event("GET", 200));
        service.onRequestHandled(event("POST", 400));
        service.getFinancialSummary(null, FROM, TO);
        verify(repository, times(1)).salesByShop(eq("shop-1"), any(), any());

        service.onRequestHandled(event("POST", 201)); // e.g. a new invoice
        service.getFinancialSummary(null, FROM, TO);
        verify(repository, times(2)).salesByShop(eq("shop-1"), any(), any());
    }

    private static ServletRequestHandledEvent event(String method, int status) {
        return new ServletRequestHandledEvent(new Object(), "/v1/api/x", "127.0.0.1", method, "dispatcher",
                null, null, 5, null, status);
    }
}
