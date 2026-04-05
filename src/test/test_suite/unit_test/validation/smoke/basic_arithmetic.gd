extends Node

func _ready() -> void:
    var target = get_parent().get_node_or_null("__UNIT_TEST_TARGET_NODE_NAME__")
    if target == null:
        push_error("Target node missing.")
        return

    var result = int(target.call("compute"))
    var runtime_class = String(target.get_class())
    if result == 10 and runtime_class == "ArithmeticSmoke":
        print("__UNIT_TEST_PASS_MARKER__")
    else:
        push_error("ArithmeticSmoke validation failed: result=%s, class=%s" % [result, runtime_class])
