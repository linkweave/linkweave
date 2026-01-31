package org.chainlink.infrastructure.db;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Parameter;
import jakarta.persistence.TemporalType;
import jakarta.persistence.TypedQuery;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import static java.util.Objects.requireNonNull;

public class SmartTypedQuery<X> implements TypedQuery<X> {

    private final TypedQuery<X> delegate;

    public SmartTypedQuery(@NonNull TypedQuery<X> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Liefert ein Non-Null (im SQL- *und* Java-Sinne) Result zurueck!
     * <p>
     * Ist also nicht geeignet, wenn das SQL-Query ein Ergebnis mit Value null liefert!
     */
    @NonNull
    public Optional<@NonNull X> getSingleResultOpt() {
        try {
            return Optional.of(delegate.getSingleResult());
        } catch (NoResultException ignored) {
            return Optional.empty();
        }
    }

    @Override
    @NonNull
    public List<@NonNull X> getResultList() {
        return delegate.getResultList();
    }

    @Override
    @Nullable
    public X getSingleResult() {
        return delegate.getSingleResult();
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setHint(@NonNull String hintName, @Nullable Object value) {
        return new SmartTypedQuery<>(delegate.setHint(hintName, value));
    }

    @Override
    public <T> @NonNull SmartTypedQuery<X> setParameter(@NonNull Parameter<T> param, @Nullable T value) {
        return new SmartTypedQuery<>(delegate.setParameter(param, value));
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setParameter(
        @NonNull Parameter<Calendar> param,
        @Nullable Calendar value,
        @NonNull TemporalType temporalType
    ) {
        return new SmartTypedQuery<>(delegate.setParameter(param, value, temporalType));
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setParameter(
        @NonNull Parameter<Date> param,
        @Nullable Date value,
        @NonNull TemporalType temporalType
    ) {
        return new SmartTypedQuery<>(delegate.setParameter(param, value, temporalType));
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setParameter(@NonNull String name, @Nullable Object value) {
        return new SmartTypedQuery<>(delegate.setParameter(name, value));
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setParameter(
        @NonNull String name,
        @Nullable Calendar value,
        @NonNull TemporalType temporalType
    ) {
        return new SmartTypedQuery<>(delegate.setParameter(name, value, temporalType));
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setParameter(
        @NonNull String name,
        @Nullable Date value,
        @NonNull TemporalType temporalType
    ) {
        return new SmartTypedQuery<>(delegate.setParameter(name, value, temporalType));
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setParameter(int position, @Nullable Object value) {
        return new SmartTypedQuery<>(delegate.setParameter(position, value));
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setParameter(
        int position,
        @Nullable Calendar value,
        @NonNull TemporalType temporalType
    ) {
        return new SmartTypedQuery<>(delegate.setParameter(position, value, temporalType));
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setParameter(
        int position,
        @Nullable Date value,
        @NonNull TemporalType temporalType
    ) {
        return new SmartTypedQuery<>(delegate.setParameter(position, value, temporalType));
    }

    @Override
    public int executeUpdate() {
        return delegate.executeUpdate();
    }

    @Override
    public int getMaxResults() {
        return delegate.getMaxResults();
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setMaxResults(int maxResult) {
        return new SmartTypedQuery<>(delegate.setMaxResults(maxResult));
    }

    @Override
    public int getFirstResult() {
        return delegate.getFirstResult();
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setFirstResult(int startPosition) {
        return new SmartTypedQuery<>(delegate.setFirstResult(startPosition));
    }

    @Override
    @NonNull
    public Map<@NonNull String, @Nullable Object> getHints() {
        return delegate.getHints();
    }

    @Override
    @NonNull
    public Set<@NonNull Parameter<?>> getParameters() {
        return delegate.getParameters();
    }

    @Override
    @NonNull
    public Parameter<?> getParameter(@NonNull String name) {
        return delegate.getParameter(name);
    }

    @Override
    public <T> @NonNull Parameter<T> getParameter(@NonNull String name, @NonNull Class<T> type) {
        return delegate.getParameter(name, type);
    }

    @Override
    @NonNull
    public Parameter<?> getParameter(int position) {
        return delegate.getParameter(position);
    }

    @Override
    public <T> @NonNull Parameter<T> getParameter(int position, @NonNull Class<T> type) {
        return delegate.getParameter(position, type);
    }

    @Override
    public boolean isBound(@NonNull Parameter<?> param) {
        return delegate.isBound(param);
    }

    @Override
    public <T> T getParameterValue(@NonNull Parameter<T> param) {
        return delegate.getParameterValue(param);
    }

    @Override
    @Nullable
    public Object getParameterValue(@NonNull String name) {
        return delegate.getParameterValue(name);
    }

    @Override
    @Nullable
    public Object getParameterValue(int position) {
        return delegate.getParameterValue(position);
    }

    @Override
    @NonNull
    public FlushModeType getFlushMode() {
        return delegate.getFlushMode();
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setFlushMode(@NonNull FlushModeType flushMode) {
        return new SmartTypedQuery<>(delegate.setFlushMode(flushMode));
    }

    @Override
    @NonNull
    public LockModeType getLockMode() {
        return delegate.getLockMode();
    }

    @Override
    @NonNull
    public SmartTypedQuery<X> setLockMode(@NonNull LockModeType lockMode) {
        return new SmartTypedQuery<>(delegate.setLockMode(lockMode));
    }

    @Override
    public <T> @NonNull T unwrap(@NonNull Class<T> cls) {
        return delegate.unwrap(cls);
    }
}
