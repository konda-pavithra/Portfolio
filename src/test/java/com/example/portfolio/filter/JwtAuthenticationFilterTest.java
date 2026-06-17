package com.example.portfolio.filter;

import com.example.portfolio.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtUtil     jwtUtil;
    @Mock FilterChain filterChain;

    @InjectMocks JwtAuthenticationFilter filter;

    private MockHttpServletRequest  request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    // ── Bearer header ─────────────────────────────────────────────────────────

    @Test
    void doFilterInternal_validBearerToken_setsSecurityContext() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        when(jwtUtil.validateToken("valid-jwt-token")).thenReturn(true);
        when(jwtUtil.extractUsername("valid-jwt-token")).thenReturn("john_doe");

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("john_doe", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_invalidBearerToken_doesNotSetSecurityContext() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer bad-token");
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // ── Query-parameter fallback (for SSE EventSource) ────────────────────────

    @Test
    void doFilterInternal_queryParamToken_setsSecurityContext() throws ServletException, IOException {
        request.setParameter("token", "sse-jwt-token");
        when(jwtUtil.validateToken("sse-jwt-token")).thenReturn(true);
        when(jwtUtil.extractUsername("sse-jwt-token")).thenReturn("john_doe");

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_invalidQueryParamToken_doesNotSetSecurityContext() throws ServletException, IOException {
        request.setParameter("token", "bad-sse-token");
        when(jwtUtil.validateToken("bad-sse-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // ── No token present ──────────────────────────────────────────────────────

    @Test
    void doFilterInternal_noToken_passesRequestThrough() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void doFilterInternal_bearerPrefixWithoutToken_noAuthSet() throws ServletException, IOException {
        // "Bearer " with no actual token — filter extracts "" which fails validation
        request.addHeader("Authorization", "Bearer ");
        when(jwtUtil.validateToken("")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // ── Header takes precedence over query param ──────────────────────────────

    @Test
    void doFilterInternal_bothHeaderAndParam_headerTakesPrecedence() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer header-token");
        request.setParameter("token", "param-token");
        when(jwtUtil.validateToken("header-token")).thenReturn(true);
        when(jwtUtil.extractUsername("header-token")).thenReturn("from_header");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("from_header", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(jwtUtil, never()).validateToken("param-token");
    }
}
