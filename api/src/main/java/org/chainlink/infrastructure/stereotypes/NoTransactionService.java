package org.chainlink.infrastructure.stereotypes;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@RequestScoped
@Transactional(TxType.NOT_SUPPORTED)
@Stereotype
@Target(TYPE)
@Retention(RUNTIME)
@Documented
public @interface NoTransactionService {
}
