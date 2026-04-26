package gd.script.gdcc.type;

public sealed interface GdPureVectorType extends GdVectorType permits GdFloatVectorType, GdIntVectorType {
    int getDimension();
}
