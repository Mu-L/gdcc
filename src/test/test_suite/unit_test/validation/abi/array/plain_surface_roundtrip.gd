extends Node

func _ready() -> void:
    var target = get_parent().get_node_or_null("__UNIT_TEST_TARGET_NODE_NAME__")
    if target == null:
        push_error("Target node missing.")
        return

    var plain = [Node.new(), 7, "leaf"]
    var echoed: Array = target.accept_and_echo(plain)
    var payloads: Array = target.payloads
    if not echoed.is_typed() and not payloads.is_typed() and echoed.size() == 3 and payloads.size() == 3 and echoed[0] is Node and payloads[0] is Node and int(echoed[1]) == 7 and int(payloads[1]) == 7 and String(echoed[2]) == "leaf" and String(payloads[2]) == "leaf" and int(target.call("read_payload_size")) == 3:
        print("__UNIT_TEST_PASS_MARKER__")
    else:
        push_error("Plain array surface roundtrip validation failed.")
