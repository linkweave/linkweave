package org.chainlink.infrastructure.json;

import java.time.LocalDateTime;
import java.util.Objects;

import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import ch.dvbern.dvbstarter.i18n.datetime.DateConst;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Value
@JaxDTO
@Schema(description = "Informationen and metadata about an entity")
public class EntityInfoJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    private LocalDateTime timestampErstellt;

    @NotNull
    @NonNull
    @Schema(required = true)
    private LocalDateTime timestampMutiert;

    @NotNull
    @NonNull
    @Schema(required = true)
    private String userErstellt;

    @NotNull
    @NonNull
    @Schema(required = true)
    private String userMutiert;

    @NonNull
    public static <E extends AbstractEntity<E>> EntityInfoJson fromEntity(@NonNull AbstractEntity<E> entity) {
        return new EntityInfoJson(
            entity.getTimestampErstellt().atZoneSameInstant(DateConst.ZONE_ID).toLocalDateTime(),
            entity.getTimestampMutiert().atZoneSameInstant(DateConst.ZONE_ID).toLocalDateTime(),
            Objects.requireNonNull(entity.getUserErstellt()),
            Objects.requireNonNull(entity.getUserMutiert())
        );
    }
}
