class_name DictionaryMutationAndLookupSmoke
extends Node

func summarize() -> int:
    var scores: Dictionary = Dictionary()
    var alpha_flag := 0
    var gamma_flag := 0
    var alpha_key: Variant = "alpha"
    var beta_key: Variant = "beta"
    var gamma_key: Variant = "gamma"
    scores[alpha_key] = 2
    scores[beta_key] = 5
    scores[alpha_key] = int(scores[alpha_key]) + 4
    if scores.has(alpha_key):
        alpha_flag = 1
    if scores.has(gamma_key):
        gamma_flag = 1
    return scores.size() * 1000 + int(scores[alpha_key]) * 100 + int(scores[beta_key]) * 10 + alpha_flag * 2 + gamma_flag
