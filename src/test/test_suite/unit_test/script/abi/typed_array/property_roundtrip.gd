class_name TypedArrayPropertyRoundtripAbiSmoke
extends Node

var payloads: Array[Node]

func read_payload_size() -> int:
    return payloads.size()
