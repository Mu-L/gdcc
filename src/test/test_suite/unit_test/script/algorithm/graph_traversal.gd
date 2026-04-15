class_name GraphTraversalSmoke
extends Node

func build_graph() -> Dictionary:
    var graph: Dictionary = Dictionary()
    var a_key: Variant = 1
    var b_key: Variant = 2
    var c_key: Variant = 3
    var d_key: Variant = 4
    var e_key: Variant = 5
    var f_key: Variant = 6
    var a_neighbors: Array = Array()
    var b_neighbors: Array = Array()
    var c_neighbors: Array = Array()
    var d_neighbors: Array = Array()
    var e_neighbors: Array = Array()
    var f_neighbors: Array = Array()

    a_neighbors.push_back(2)
    a_neighbors.push_back(3)
    b_neighbors.push_back(4)
    c_neighbors.push_back(5)
    d_neighbors.push_back(6)
    e_neighbors.push_back(6)

    graph[a_key] = a_neighbors
    graph[b_key] = b_neighbors
    graph[c_key] = c_neighbors
    graph[d_key] = d_neighbors
    graph[e_key] = e_neighbors
    graph[f_key] = f_neighbors
    return graph

func bfs_order() -> int:
    var graph = build_graph()
    var queue: Array = Array()
    var seen: Dictionary = Dictionary()
    var cursor := 0
    var order := 0
    var start_key: Variant = 1
    queue.push_back(1)
    seen[start_key] = true

    while cursor < queue.size():
        var current = int(queue[cursor])
        var current_key: Variant = current
        var neighbors = graph[current_key]
        var index := 0
        order = order * 10 + current
        while index < neighbors.size():
            var next = int(neighbors[index])
            var next_key: Variant = next
            if not seen.has(next_key):
                seen[next_key] = true
                queue.push_back(next)
            index = index + 1
        cursor = cursor + 1
    return order

func dfs_order() -> int:
    var seen: Dictionary = Dictionary()
    return dfs_visit(1, build_graph(), seen, 0)

func dfs_visit(current: int, graph: Dictionary, seen: Dictionary, seed: int) -> int:
    var current_key: Variant = current
    if seen.has(current_key):
        return seed
    seen[current_key] = true

    var order := seed * 10 + current
    var neighbors = graph[current_key]
    var index := 0
    while index < neighbors.size():
        order = dfs_visit(int(neighbors[index]), graph, seen, order)
        index = index + 1
    return order
