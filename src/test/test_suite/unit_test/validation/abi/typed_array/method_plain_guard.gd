# gdcc-test: output_contains=Invalid type in function
# gdcc-test: output_not_contains=frontend typed array method plain guard after bad call.
extends Node

func _ready() -> void:
    var target = get_parent().get_node_or_null("__UNIT_TEST_TARGET_NODE_NAME__")
    if target == null:
        push_error("Target node missing.")
        return

    var exact: Array[Node] = [Node.new()]
    if int(target.call("accept_payloads", exact)) != 11 or int(target.call("read_accept_calls")) != 1:
        push_error("Typed array plain-guard setup failed.")
        return

    # Emit the pass marker immediately before the intentionally failing boundary call.
    print("__UNIT_TEST_PASS_MARKER__")
    target.call("accept_payloads", [Node.new()])
    print("frontend typed array method plain guard after bad call.")
