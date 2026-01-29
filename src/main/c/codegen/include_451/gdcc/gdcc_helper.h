#ifndef GDCC_HELPER_H
#define GDCC_HELPER_H

#include <gdextension-lite.h>
#include "gdcc_string_name.h"
#include "gdcc_string.h"
#include "gdcc_call.h"
#include "gdcc_bind.h"

#if !defined(GDE_EXPORT)
#if defined(_WIN32)
#define GDE_EXPORT __declspec(dllexport)
#elif defined(__GNUC__)
#define GDE_EXPORT __attribute__((visibility("default")))
#else
#define GDE_EXPORT
#endif
#endif

/// Object Property Getters

#define GDCC_DEFINE_OBJECT_GETTER(ReturnType, ReturnTypeName)                                 \
static inline ReturnType gdcc_object_get_##ReturnTypeName(                                    \
    GDExtensionConstObjectPtr obj, const godot_StringName* property_name) {                   \
        godot_Variant result = godot_Object_get(obj, property_name);                          \
        ReturnType value = godot_new_##ReturnTypeName##_with_Variant(&result);                \
        godot_variant_destroy(&result);                                                       \
    return value;                                                                             \
}

GDCC_DEFINE_OBJECT_GETTER(godot_float, float)
GDCC_DEFINE_OBJECT_GETTER(godot_bool, bool)
GDCC_DEFINE_OBJECT_GETTER(godot_int, int)
GDCC_DEFINE_OBJECT_GETTER(godot_Vector3, Vector3)

#define GDCC_DEFINE_OBJECT_SETTER(ValueType, ValueTypeName)                                \
static inline void gdcc_object_set_##ValueTypeName(                                        \
    GDExtensionObjectPtr obj, const godot_StringName* property_name, ValueType value) {    \
    godot_Variant v = godot_new_Variant_with_##ValueTypeName(value);                       \
    godot_Object_set(obj, property_name, &v);                                              \
    godot_variant_destroy(&v);                                                             \
}

GDCC_DEFINE_OBJECT_SETTER(godot_float, float)
GDCC_DEFINE_OBJECT_SETTER(godot_bool, bool)
GDCC_DEFINE_OBJECT_SETTER(godot_int, int)
GDCC_DEFINE_OBJECT_SETTER(godot_Vector3*, Vector3)

#define godot_Nil godot_Variant
#define godot_TypedDictionary(key, value)  godot_Dictionary

static godot_Engine* _gd_engine;

static void gdcc_init() {
    _gd_engine = godot_Engine_singleton();
}

static bool gdcc_is_editor_hint() {
    return godot_Engine_is_editor_hint(_gd_engine);
}

#endif //GDCC_HELPER_H
