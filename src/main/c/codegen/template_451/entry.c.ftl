<#-- @ftlvariable name="module" type="dev.superice.gdcc.lir.LirModule" -->
<#-- @ftlvariable name="helper" type="dev.superice.gdcc.backend.c.gen.CGenHelper" -->
<#include "trim.ftl">

#include "entry.h"
#include <gdcc_helper.h>
#include <implementation-macros.h>

GDE_EXPORT GDExtensionBool gdextension_entry(
    GDExtensionInterfaceGetProcAddress p_get_proc_address,
    GDExtensionClassLibraryPtr p_library,
    GDExtensionInitialization* r_initialization
) {
    gdextension_lite_initialize(p_get_proc_address);
    class_library = p_library;

    r_initialization->initialize = &initialize;
    r_initialization->deinitialize = &deinitialize;

    return true;
}

void initialize(void*, const GDExtensionInitializationLevel p_level) {
    if (p_level != GDEXTENSION_INITIALIZATION_SCENE) {
        return;
    }
    gdcc_init();
    <#--  Print start loading  -->
    {
        godot_Variant msg_variant = godot_new_Variant_with_String(GD_STATIC_S(u8"Loading ${module.moduleName}..."));
        godot_print(&msg_variant, NULL, 0);
        godot_Variant_destroy(&msg_variant);
    }
    // Register user classes
    <#list module.classDefs as classDef>
    {
        GDExtensionClassCreationInfo5 creation_info = {};
        creation_info.is_abstract = ${classDef.abstract?c};
        creation_info.is_runtime = false;
        creation_info.is_virtual = false;
        creation_info.is_exposed = true;
        creation_info.create_instance_func = ${classDef.name}_class_create_instance;
        creation_info.free_instance_func = ${classDef.name}_class_free_instance;
        creation_info.get_virtual_call_data_func = ${classDef.name}_class_get_virtual_with_data;
        creation_info.call_virtual_with_data_func = ${classDef.name}_class_call_virtual_with_data;
        godot_classdb_register_extension_class5(class_library,
                                                GD_STATIC_SN(u8"${classDef.name}"), GD_STATIC_SN(u8"${classDef.superName}"),
                                                &creation_info);
        GDRotatingCamera3D_class_bind_methods();
    }
    </#list>
}

void deinitialize(void*, GDExtensionInitializationLevel p_level) {
    <#--  Print start unloading  -->
    {
        godot_Variant msg_variant = godot_new_Variant_with_String(GD_STATIC_S(u8"Unloading ${module.moduleName}..."));
        godot_print(&msg_variant, NULL, 0);
        godot_Variant_destroy(&msg_variant);
    }
    <#--  Destroy Const StringNames and Strings  -->
    gdcc_sn_registry_destroy_all();
    gdcc_s_registry_destroy_all();
}

// Bind Methods for each class
<#list module.classDefs as classDef>
void ${classDef.name}_class_bind_methods() {
    godot_StringName* class_name = GD_STATIC_SN(u8"${classDef.name}");
    // Properties
    <#list classDef.properties as property>
    {
        <#if !property.static>
            <@t width=4/>${helper.renderGetterBindName(property)}(class_name, GD_STATIC_SN(u8"${property.name}"));
        </#if>
    }
    </#list>
}
</#list>