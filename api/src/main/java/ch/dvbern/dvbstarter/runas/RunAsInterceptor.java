package ch.dvbern.dvbstarter.runas;

import java.util.Set;

import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lombok.RequiredArgsConstructor;

// I saw that https://github.com/quarkusio/quarkus/blob/3289f66d49c1d843517668c60abee6c9a42d7273/independent-projects/arc/runtime/src/main/java/io/quarkus/arc/impl/ActivateRequestContextInterceptor.java#L20 has
// PLATFORM_BEFORE + 100 and to me it makes sense to run after
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 150)
@Interceptor()
@RunAs(username = "", roles = {})
@RequiredArgsConstructor
class RunAsInterceptor {

    private final CurrentIdentityAssociation association;

    @AroundInvoke
    public Object invoke(InvocationContext context) throws Exception {
        var existing = association.getIdentity();
        try {
            RunAs runAs = context.getMethod().getAnnotation(RunAs.class);
            var identity = QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal(runAs.username()))
                .addRoles(Set.of(runAs.roles()))
                .build();
            association.setIdentity(identity);
            return context.proceed();
        } finally {
            association.setIdentity(existing);
        }
    }
}
