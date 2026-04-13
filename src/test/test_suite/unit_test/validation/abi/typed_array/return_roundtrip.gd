extends Node

func _ready() -> void:
    var target = get_parent().get_node_or_null("__UNIT_TEST_TARGET_NODE_NAME__")
    if target == null:
        push_error("Target node missing.")
        return

    var exact: Array[Node] = [target, null]
    var payloads: Array[Node] = target.echo_payloads(exact)
    if payloads.get_typed_builtin() == TYPE_OBJECT and payloads.get_typed_class_name() == &"Node" and payloads.get_typed_script() == null and payloads.size() == 2 and payloads[0] == target and payloads[1] == null:
        print("__UNIT_TEST_PASS_MARKER__")
    else:
        push_error("Typed array return roundtrip validation failed.")
