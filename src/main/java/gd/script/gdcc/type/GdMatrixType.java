package gd.script.gdcc.type;

public sealed interface GdMatrixType extends GdCompoundVectorType
        permits GdProjectionType, GdTransform2DType, GdTransform3DType {
}
