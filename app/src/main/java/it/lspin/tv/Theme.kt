package it.lspin.tv

/**
 * Tavolozza dell'interfaccia. Un solo interruttore:
 *   PRO = false  ->  tema originale (viola/blu neon)
 *   PRO = true   ->  tema LSPIN (nero, blu e bianco, sobrio)
 */
object Theme {
    const val PRO = true

    private fun pick(neon: Long, pro: Long): Int = (if (PRO) pro else neon).toInt()

    val BG = pick(0xFF05060F, 0xFF0B1220)
    val ACCENT = pick(0xFF7B4DFF, 0xFF1A98FF)
    val ACCENT2 = pick(0xFF3D6BFF, 0xFF0F5BD8)
    val NEON = pick(0xFFA06BFF, 0xFF4FC3F7)           // alone del cursore e barrette sezione
    val BAR = pick(0xFF0B0E22, 0xFF0A101C)            // barre superiori (browser, file, terminale)
    val PILL = pick(0x88101428, 0x3DFFFFFF)           // sfondo della barra in alto
    val CARD = pick(0x66101A3A, 0x66101828)           // righe delle impostazioni
    val VALUE = pick(0xFFB9A2FF, 0xFFFFFFFF)          // valori nelle impostazioni, prompt terminale
    val SELECT = pick(0x557B4DFF, 0x5500C2FF)         // riga selezionata nei file
    val BUBBLE0 = pick(0xFF7A4DFF, 0xF2FFFFFFL)        // bolla attiva della barra in alto
    val BUBBLE1 = pick(0xFF3558FF, 0xCCEAF0FFL)
    val GLOW1 = pick(0x663B1BFF, 0x407B2FFF)          // aloni negli angoli in basso
    val GLOW2 = pick(0x441A4BFF, 0x2800C2FF)
    val HERO_TITLE = pick(0xFFEBD9B8, 0xFFFFFFFF)     // titolo del film a destra
    val SHADE = pick(0x050612, 0x0B1220)              // colore (RGB) delle sfumature sul banner

    // animazione di accensione
    val BOOT_BG = pick(0xFF05060F, 0xFF000000)      // nero puro come la bootanimation
    val BOOT_TINT = pick(0xFFE9E4FF, 0xFFFFFFFF)
    val BOOT_ACCENT0 = pick(0xFF9373FF, 0xFF2F6BFF)
    val BOOT_ACCENT1 = pick(0xFF3E6BFF, 0xFF1F56E0)
    val BOOT_HAZE = pick(0x526B4DFF, 0x00000000)

    /** Tema colorato: ogni tile mantiene i suoi colori. */
    fun tileTop(big: Boolean, orig: Int): Int = orig
    fun tileBottom(big: Boolean, orig: Int): Int = orig
}
