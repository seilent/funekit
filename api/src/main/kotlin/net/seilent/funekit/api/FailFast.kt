package net.seilent.funekit.api

import kotlin.coroutines.CoroutineContext

object FailFast : CoroutineContext.Element, CoroutineContext.Key<FailFast> {
    override val key: CoroutineContext.Key<*> get() = this
}
