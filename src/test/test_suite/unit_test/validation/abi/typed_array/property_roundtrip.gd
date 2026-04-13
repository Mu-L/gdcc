extends Node

func _ready() -> void:
    var target = get_parent().get_node_or_null("__UNIT_TEST_TARGET_NODE_NAME__")
    if target == null:
        push_error("Target node missing.")
        return

    var initial: Array[Node] = target.payloads
    if initial.get_typed_builtin() != TYPE_OBJECT or initial.get_typed_class_name() != &"Node" or initial.get_typed_script() != null or not initial.is_empty():
        push_error("Typed array property default initialization validation failed.")
        return

    var exact: Array[Node] = [Node.new(), null]
    target.payloads = exact

    var payloads: Array[Node] = target.payloads
    if payloads.get_typed_builtin() == TYPE_OBJECT and payloads.get_typed_class_name() == &"Node" and payloads.get_typed_script() == null and payloads.size() == 2 and payloads[1] == null and int(target.call("read_payload_size")) == 2:
        print("__UNIT_TEST_PASS_MARKER__")
    else:
        push_error("Typed array property roundtrip validation failed.")
