class_name ArraySumAndMutationSmoke
extends Node

func summarize() -> int:
    var values: Array = Array()
    values.push_back(2)
    values.push_back(4)
    values.push_back(6)
    values.push_back(8)
    values[1] = int(values[1]) + 5

    var index := 0
    var total := 0
    while index < values.size():
        total = total + int(values[index])
        index = index + 1
    return values.size() * 1000 + total * 10 + int(values[1])
