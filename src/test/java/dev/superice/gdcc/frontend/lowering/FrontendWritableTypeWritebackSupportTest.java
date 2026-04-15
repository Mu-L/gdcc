package dev.superice.gdcc.frontend.lowering;

import dev.superice.gdcc.type.GdArrayType;
import dev.superice.gdcc.type.GdDictionaryType;
import dev.superice.gdcc.type.GdIntType;
import dev.superice.gdcc.type.GdObjectType;
import dev.superice.gdcc.type.GdPackedNumericArrayType;
import dev.superice.gdcc.type.GdVariantType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendWritableTypeWritebackSupportTest {
    @Test
    void requiresReverseCommitForCarrierTypeMatchesSharedTypeMatrix() {
        assertAll(
                () -> assertFalse(FrontendWritableTypeWritebackSupport.requiresReverseCommitForCarrierType(GdIntType.INT)),
                () -> assertFalse(FrontendWritableTypeWritebackSupport.requiresReverseCommitForCarrierType(GdObjectType.OBJECT)),
                () -> assertFalse(FrontendWritableTypeWritebackSupport.requiresReverseCommitForCarrierType(
                        new GdArrayType(GdVariantType.VARIANT)
                )),
                () -> assertFalse(FrontendWritableTypeWritebackSupport.requiresReverseCommitForCarrierType(
                        new GdDictionaryType(GdVariantType.VARIANT, GdVariantType.VARIANT)
                )),
                () -> assertTrue(FrontendWritableTypeWritebackSupport.requiresReverseCommitForCarrierType(
                        GdPackedNumericArrayType.PACKED_INT32_ARRAY
                )),
                () -> assertTrue(FrontendWritableTypeWritebackSupport.requiresReverseCommitForCarrierType(
                        GdVariantType.VARIANT
                ))
        );
    }
}
