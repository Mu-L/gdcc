extends Node

func _ready() -> void:
    var target = get_parent().get_node_or_null("__UNIT_TEST_TARGET_NODE_NAME__")
    if target == null:
        push_error("Target node missing.")
        return

    var summary = int(target.call("summarize", 7))
    if summary == 7358:
        print("__UNIT_TEST_PASS_MARKER__")
    else:
        push_error("FibonacciSequenceSmoke validation failed.")
