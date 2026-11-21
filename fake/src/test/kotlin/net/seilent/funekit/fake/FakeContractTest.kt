package net.seilent.funekit.fake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import net.seilent.funekit.api.ApiContract
import net.seilent.funekit.api.Edge
import net.seilent.funekit.api.Subject

@OptIn(ExperimentalCoroutinesApi::class)
class FakeContractTest : ApiContract() {
    override fun TestScope.open(edge: Edge): Subject = fakeSubject(edge)
}
