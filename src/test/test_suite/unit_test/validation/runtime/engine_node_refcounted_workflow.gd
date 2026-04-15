extends Node

func _ready() -> void:
    var target = get_parent().get_node_or_null("__UNIT_TEST_TARGET_NODE_NAME__")
    if target == null:
        push_error("Target node missing.")
        return

    var child_count = int(target.call("engine_node_child_count"))
    var runtime_class = String(target.call("engine_node_class_name"))
    var ref_count = int(target.call("engine_ref_count"))
    if child_count == 0 and runtime_class == "Node" and ref_count >= 1:
        print("__UNIT_TEST_PASS_MARKER__")
    else:
        push_error("EngineNodeRefCountedWorkflowSmoke validation failed.")
