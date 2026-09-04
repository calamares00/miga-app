package com.bmo00.miga.data.model

enum class Difficulty(val label: String) {
    FACIL("Fácil"),
    MEDIA("Media"),
    DIFICIL("Difícil")
}

enum class SortOption(val label: String) {
    NAME_ASC("Nombre (A-Z)"),
    RECENT("Más reciente"),
    MOST_COOKED("Más cocinada"),
    PREP_TIME("Tiempo de preparación")
}
