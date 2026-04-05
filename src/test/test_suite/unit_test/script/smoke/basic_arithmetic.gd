class_name ArithmeticSmoke
extends Node

func compute() -> int:
    var total := 0
    var value := 1
    while value <= 4:
        total = total + value
        value = value + 1
    return total
