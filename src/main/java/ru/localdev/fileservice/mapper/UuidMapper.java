package ru.localdev.fileservice.mapper;

import ru.sbercom.platform.UuidProto;

import java.util.UUID;

/**
 * Конвертация между proto-UUID (fixed64 high/low, big-endian) и {@link java.util.UUID}.
 * high = старшие 64 бита, low = младшие — что совпадает с
 * {@link UUID#getMostSignificantBits()} / {@link UUID#getLeastSignificantBits()}.
 */
public final class UuidMapper {

    private UuidMapper() {
    }

    public static UuidProto.UUID toProto(UUID uuid) {
        return UuidProto.UUID.newBuilder()
                .setHigh(uuid.getMostSignificantBits())
                .setLow(uuid.getLeastSignificantBits())
                .build();
    }

    public static UUID toJava(UuidProto.UUID proto) {
        return new UUID(proto.getHigh(), proto.getLow());
    }
}
