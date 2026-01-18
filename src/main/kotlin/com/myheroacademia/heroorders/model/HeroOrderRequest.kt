package com.myheroacademia.heroorders.model

data class HeroOrderRequest(
    val heroId: String,
    val heroName: String,
    val priority: Int
)
