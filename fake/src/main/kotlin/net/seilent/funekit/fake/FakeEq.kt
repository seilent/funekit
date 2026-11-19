package net.seilent.funekit.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.seilent.funekit.api.Eq
import net.seilent.funekit.api.EqResult

class FakeEq internal constructor() : Eq {
    private val _profile = MutableStateFlow<String?>(null)

    override val profile: StateFlow<String?> = _profile.asStateFlow()

    override suspend fun setEq(profile: String?): EqResult {
        if (profile != null && profile.lineSequence().none { it.startsWith("Preamp:") || it.startsWith("Filter") }) {
            return EqResult(false, null, listOf(REFUSED))
        }
        _profile.value = profile
        return EqResult(true, profile, emptyList())
    }

    private companion object {
        const val REFUSED = "no valid filters"
    }
}
