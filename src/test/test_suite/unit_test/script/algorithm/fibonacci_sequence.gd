class_name FibonacciSequenceSmoke
extends Node

func build_sequence(limit: int) -> Array:
    var sequence: Array = Array()
    sequence.push_back(0)
    sequence.push_back(1)
    while sequence.size() < limit:
        var next_value := int(sequence[sequence.size() - 1]) + int(sequence[sequence.size() - 2])
        sequence.push_back(next_value)
    return sequence

func summarize(limit: int) -> int:
    var sequence = build_sequence(limit)
    return sequence.size() * 1000 + int(sequence[4]) * 100 + int(sequence[5]) * 10 + int(sequence[6])
