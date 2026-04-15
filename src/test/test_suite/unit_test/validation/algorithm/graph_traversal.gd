extends Node

func _ready() -> void:
    var target = get_parent().get_node_or_null("__UNIT_TEST_TARGET_NODE_NAME__")
    if target == null:
        push_error("Target node missing.")
        return

    var bfs = int(target.call("bfs_order"))
    var dfs = int(target.call("dfs_order"))
    if bfs == 123456 and dfs == 124635:
        print("__UNIT_TEST_PASS_MARKER__")
    else:
        push_error("GraphTraversalSmoke validation failed.")
