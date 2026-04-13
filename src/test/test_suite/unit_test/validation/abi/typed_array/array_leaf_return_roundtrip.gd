extends Node

func _ready() -> void:
    var target = get_parent().get_node_or_null("__UNIT_TEST_TARGET_NODE_NAME__")
    if target == null:
        push_error("Target node missing.")
        return

    var exact: Array[Array] = [[1, 2], []]
    var groups: Array[Array] = target.echo_groups(exact)
    var first: Array = groups[0]
    var second: Array = groups[1]
    if groups.get_typed_builtin() == TYPE_ARRAY and groups.size() == 2 and not first.is_typed() and first.size() == 2 and int(first[0]) == 1 and int(first[1]) == 2 and not second.is_typed() and second.is_empty():
        print("__UNIT_TEST_PASS_MARKER__")
    else:
        push_error("Typed array Array-leaf return roundtrip validation failed.")
