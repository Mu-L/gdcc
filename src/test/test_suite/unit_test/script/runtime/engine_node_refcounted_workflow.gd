class_name EngineNodeRefCountedWorkflowSmoke
extends Node

func engine_node_child_count() -> int:
    return Node.new().get_child_count()

func engine_node_class_name() -> String:
    return Node.new().get_class()

func engine_ref_count() -> int:
    return RefCounted.new().get_reference_count()
