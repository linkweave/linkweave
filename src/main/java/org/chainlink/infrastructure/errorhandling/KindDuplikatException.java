package org.chainlink.infrastructure.errorhandling;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import ch.dvbern.dvbstarter.shared.types.id.ID;
import ch.dvbern.oss.commons.i18nl10n.I18nMessage;
import ch.dvbern.oss.hemed.esc.api.features.kinder.Kind;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Getter
public final class KindDuplikatException extends AppException {

    @Serial
    private static final long serialVersionUID = 2654097053149795616L;

    private final List<ID<Kind>> kindDuplikatIds = new ArrayList<>();

    public KindDuplikatException(@NonNull List<ID<Kind>> ids) {
       super(I18nMessage.of("AppValidation.KIND_DUPLIKAT"), ExceptionId.random(), null);
       kindDuplikatIds.addAll(ids);
    }
}
