package chirprea.app

/** How a picture is fitted into the radio's logo area (desktop and web). */
enum class LogoFit(val label: String) {
    Fit("Ajustar (bordas pretas)"),
    Fill("Preencher (corta as sobras)"),
    Stretch("Esticar"),
}
