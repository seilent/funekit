package net.seilent.funekit.kit.quickplay

import net.seilent.funekit.fxU
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.home.HomeGeo
import net.seilent.funekit.kit.home.HomeMenu
import net.seilent.funekit.kit.touch.ListTouch

fun qpLandAim(qp: QuickplayState, touch: ListTouch, l: QpLayout) {
    qp.landAim()?.let {
        touch.state.dispatchRawDelta(fxU(it - qpPos(touch, qp, l)) * l.s.stdPpu)
        qp.rest(it, touch.state.firstVisibleItemScrollOffset)
    }
}

fun homeLandAim(menu: HomeMenu, touch: ListTouch, geo: HomeGeo) {
    menu.landAim()?.let {
        touch.state.dispatchRawDelta((it - geo.offU(touch.state.firstVisibleItemScrollOffset)) * geo.s.stdPpu)
        menu.rest(it, touch.state.firstVisibleItemScrollOffset)
    }
}

fun qpPanStart(front: Boolean, geo: HomeGeo, menu: HomeMenu, homeTouch: ListTouch, qp: QuickplayState, qpTouch: ListTouch, l: QpLayout, toast: Toast) {
    qp.dropKeyFocus()
    toast.hide()
    homeTouch.halt()
    homeLandAim(menu, homeTouch, geo)
    qpTouch.land()
    qpLandAim(qp, qpTouch, l)
    if (front) {
        val vis = l.visible(qp.focus, qpPos(qpTouch, qp, l), l.headerAsc())
        qp.ask(vis, l.pxOf(vis))
        qpTouch.state.requestScrollToItem(0, l.pxOf(vis))
    }
}
