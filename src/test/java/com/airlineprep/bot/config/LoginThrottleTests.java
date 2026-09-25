package com.airlineprep.bot.config;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class LoginThrottleTests {
 @Test void peerBudgetExpiresAndDoesNotLockOtherUsersOrTrustProxyHeaders() throws Exception {
  Clock clock=mock(Clock.class);Instant now=Instant.parse("2026-01-01T00:00:00Z");when(clock.instant()).thenReturn(now);
  var filter=new LoginThrottleFilter(clock);
  for(int i=0;i<10;i++) assertThat(filter.allow("peer")).isTrue();
  assertThat(filter.allow("peer")).isFalse();assertThat(filter.allow("other")).isTrue();
  var request=new MockHttpServletRequest("POST","/admin/login");request.setServletPath("/admin/login");request.setRemoteAddr("peer");request.addHeader("X-Forwarded-For","new-peer");
  var response=new MockHttpServletResponse();filter.doFilter(request,response,new MockFilterChain());
  assertThat(response.getStatus()).isEqualTo(429);assertThat(response.getHeader("Retry-After")).isEqualTo("60");
  when(clock.instant()).thenReturn(now.plusSeconds(60));assertThat(filter.allow("peer")).isTrue();
 }
 @Test void memoryIsBoundedAndUnrelatedRequestsAreNotThrottled() throws Exception {
  var filter=new LoginThrottleFilter(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
  for(int i=0;i<4096;i++) assertThat(filter.allow("peer"+i)).isTrue();
  assertThat(filter.allow("overflow")).isFalse();
  var response=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET","/admin/login"),response,new MockFilterChain());
  assertThat(response.getStatus()).isEqualTo(200);
 }
}
