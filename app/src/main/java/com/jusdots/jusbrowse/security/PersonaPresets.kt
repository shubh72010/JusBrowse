package com.jusdots.jusbrowse.security

/**
 * Adapter for existing UI to access Golden Profiles.
 * Delegates to PersonaRepository.
 */
object PersonaPresets {

    val ALL_PRESETS: List<FakePersona> get() = PersonaRepository.GOLDEN_PROFILES

    val ALL_GROUPS: List<FakePersona> get() = PersonaRepository.GOLDEN_PROFILES.filter { it.isFlagship }

    /**
     * Get a random persona
     */
    fun getRandomPersona(): FakePersona = PersonaRepository.getRandomPersona()

    /**
     * Get persona by ID
     */
    fun getById(id: String): FakePersona? = PersonaRepository.getPersonaById(id)
}
