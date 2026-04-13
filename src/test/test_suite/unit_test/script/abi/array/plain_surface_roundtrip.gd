class_name PlainArraySurfaceRoundtripAbiSmoke
extends Node

var payloads: Array

func accept_and_echo(values: Array) -> Array:
    payloads = values
    return values

func read_payload_size() -> int:
    return payloads.size()
