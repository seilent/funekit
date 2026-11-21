package net.seilent.funekit.fake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import net.seilent.funekit.api.Edge
import net.seilent.funekit.api.SeamContract
import net.seilent.funekit.api.Subject

@OptIn(ExperimentalCoroutinesApi::class)
class FakeRefsContractTest : SeamContract() {
    override fun TestScope.open(edge: Edge): Subject = fakeSubject(edge, refs = true)
}
