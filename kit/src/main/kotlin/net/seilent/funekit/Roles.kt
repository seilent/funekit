package net.seilent.funekit

import net.seilent.funekit.gen.Old
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard

enum class Weight { Light, SemiLight, Regular, Bold }

enum class Case { Lower, Upper, Mixed }

class Role(val size: Float, val weight: Weight, val case: Case, val alpha: Float, val tracking: Float)

private fun weightOf(style: String) = when (style) {
    "Light" -> Weight.Light
    "Bold" -> Weight.Bold
    "SemiLight" -> Weight.SemiLight
    else -> Weight.Regular
}

private fun caseOf(c: String) = when (c.first()) {
    'l' -> Case.Lower
    'u' -> Case.Upper
    else -> Case.Mixed
}

private fun role(size: Float, style: String, letters: String, alpha: Float, tracking: Float = Tok.tracking) =
    Role(size, weightOf(style), caseOf(letters), alpha, tracking)

private fun mixed(size: Float, style: String, alpha: Float) = Role(size, weightOf(style), Case.Mixed, alpha, Tok.tracking)

object Roles {
    val hubHeader = role(Tok.type_hubHeader_size, Tok.type_hubHeader_style, Tok.type_hubHeader_case, Tok.type_hubHeader_alpha, Tok.trackingHeader)
    val pageHeader = role(Tok.type_pageHeader_size, Tok.type_pageHeader_style, Tok.type_pageHeader_case, Tok.type_pageHeader_alpha, Tok.trackingHeader)
    val albumHeader = role(
        Tok.type_albumHeader_size, Tok.type_albumHeader_style, Tok.type_albumHeader_case, Tok.type_albumHeader_alpha,
        Tok.tracking * Tok.type_albumHeader_size / Tok.type_pivotActive_size,
    )
    val homeItem = role(Tok.type_homeItem_size, Tok.type_homeItem_style, Tok.type_homeItem_case, Tok.type_homeItem_alpha)
    val homeSubtitle = role(Tok.type_homeSubtitle_size, Tok.type_homeSubtitle_style, Tok.type_homeSubtitle_case, Tok.type_homeSubtitle_alpha)
    val pivotActive = role(Tok.type_pivotActive_size, Tok.type_pivotActive_style, Tok.type_pivotActive_case, Tok.type_pivotActive_alpha)
    val pivotInactive = role(Tok.type_pivotInactive_size, Tok.type_pivotInactive_style, Tok.type_pivotInactive_case, Tok.type_pivotInactive_alpha)
    val watermark = role(Tok.type_watermark_size, Tok.type_watermark_style, Tok.type_watermark_case, Tok.type_watermark_alpha)
    val quickplayHeader = role(Tok.type_quickplayHeader_size, Tok.type_quickplayHeader_style, Tok.type_quickplayHeader_case, Tok.type_quickplayHeader_alpha)
    val quickplayHint = role(Tok.type_quickplayHint_size, Tok.type_quickplayHint_style, Tok.type_quickplayHint_case, Tok.type_quickplayHint_alpha)
    val qpHint = role(Tok.type_qpHint_size, Tok.type_qpHint_style, Tok.type_qpHint_case, Tok.type_qpHint_alpha)
    val sectionLabel = role(Tok.type_sectionLabel_size, Tok.type_sectionLabel_style, Tok.type_sectionLabel_case, Tok.type_sectionLabel_alpha)
    val settingsItem = role(Tok.type_settingsItem_size, Tok.type_settingsItem_style, Tok.type_settingsItem_case, Tok.type_settingsItem_alpha)
    val pageRowTitle = Role(Tok.page_rowTitle, settingsItem.weight, settingsItem.case, settingsItem.alpha, settingsItem.tracking)
    val settingsValue = role(Tok.type_settingsValue_size, Tok.type_settingsValue_style, Tok.type_settingsValue_case, Tok.type_settingsValue_alpha)
    val settingsSub = role(Tok.type_settingsSub_size, Tok.type_settingsSub_style, Tok.type_settingsSub_case, Tok.type_settingsSub_alpha)
    val detailName = role(Tok.type_detailName_size, Tok.type_detailName_style, Tok.type_detailName_case, Tok.type_detailName_alpha)
    val detailTitle = role(Tok.type_detailTitle_size, Tok.type_detailTitle_style, Tok.type_detailTitle_case, Tok.type_detailTitle_alpha)
    val detailSub = role(Tok.type_detailSub_size, Tok.type_detailSub_style, Tok.type_detailSub_case, Tok.type_detailSub_alpha)
    val albcolTitle = role(Tok.type_albcolTitle_size, Tok.type_albcolTitle_style, Tok.type_albcolTitle_case, Tok.type_albcolTitle_alpha)
    val albcolArtist = role(Tok.type_albcolArtist_size, Tok.type_albcolArtist_style, Tok.type_albcolArtist_case, Tok.type_albcolArtist_alpha)
    val listPrimary = role(Tok.type_listPrimary_size, Tok.type_listPrimary_style, Tok.type_listPrimary_case, Tok.type_listPrimary_alpha)
    val listSecondary = role(Tok.type_listSecondary_size, Tok.type_listSecondary_style, Tok.type_listSecondary_case, Tok.type_listSecondary_alpha)
    val listCount = role(Tok.type_listCount_size, Tok.type_listCount_style, Tok.type_listCount_case, Tok.type_listCount_alpha)
    val listPlaylist = role(Tok.type_listPlaylist_size, Tok.type_listPlaylist_style, Tok.type_listPlaylist_case, Tok.type_listPlaylist_alpha)
    val listCollection = role(Tok.type_listCollection_size, Tok.type_listCollection_style, Tok.type_listCollection_case, Tok.type_listCollection_alpha)
    val listCollectionCount = role(Tok.type_listCollectionCount_size, Tok.type_listCollectionCount_style, Tok.type_listCollectionCount_case, Tok.type_listCollectionCount_alpha)
    val listSong = role(Tok.type_listSong_size, Tok.type_listSong_style, Tok.type_listSong_case, Tok.type_listSong_alpha)
    val listSongSub = role(Tok.type_listSongSub_size, Tok.type_listSongSub_style, Tok.type_listSongSub_case, Tok.type_listSongSub_alpha)
    val listLibSecondary = role(Tok.type_listLibSecondary_size, Tok.type_listLibSecondary_style, Tok.type_listLibSecondary_case, Tok.type_listLibSecondary_alpha)
    val letterGlyph = role(Tok.type_letterGlyph_size, Tok.type_letterGlyph_style, Tok.type_letterGlyph_case, Tok.type_letterGlyph_alpha)
    val boxCaption = role(Tok.type_boxCaption_size, Tok.type_boxCaption_style, Tok.type_boxCaption_case, Tok.type_boxCaption_alpha)
    val boxCaptionLib = role(Tok.type_boxCaptionLib_size, Tok.type_boxCaptionLib_style, Tok.type_boxCaptionLib_case, Tok.type_boxCaptionLib_alpha)
    val gridCaption = role(Tok.type_gridCaption_size, Tok.type_gridCaption_style, Tok.type_gridCaption_case, Tok.type_gridCaption_alpha)
    val dialogTitle = role(Tok.type_dialogTitle_size, Tok.type_dialogTitle_style, Tok.type_dialogTitle_case, Tok.type_dialogTitle_alpha)
    val dialogItem = role(Tok.type_dialogItem_size, Tok.type_dialogItem_style, Tok.type_dialogItem_case, Tok.type_dialogItem_alpha)
    val overlayLabel = role(Tok.type_overlayLabel_size, Tok.type_overlayLabel_style, Tok.type_overlayLabel_case, Tok.type_overlayLabel_alpha)
    val jumpExit = role(Old.type_jumpExit_size, Tok.type_jumpExit_style, Tok.type_jumpExit_case, Tok.type_jumpExit_alpha)
    val jumpCell = role(Tok.type_jumpCell_size, Tok.type_jumpCell_style, Tok.type_jumpCell_case, Tok.type_jumpCell_alpha)
    val npArtist = role(Tok.type_npArtist_size, Tok.type_npArtist_style, Tok.type_npArtist_case, Tok.type_npArtist_alpha)
    val npAlbum = role(Tok.type_npAlbum_size, Tok.type_npAlbum_style, Tok.type_npAlbum_case, Tok.type_npAlbum_alpha)
    val npTitle = role(Tok.type_npTitle_size, Tok.type_npTitle_style, Tok.type_npTitle_case, Tok.type_npTitle_alpha)
    val npNext = role(Tok.type_npNext_size, Tok.type_npNext_style, Tok.type_npNext_case, Tok.type_npNext_alpha)
    val npCount = role(Tok.type_npCount_size, Tok.type_npCount_style, Tok.type_npCount_case, Tok.type_npCount_alpha)
    val npViewArtist = role(Tok.type_npViewArtist_size, Tok.type_npViewArtist_style, Tok.type_npViewArtist_case, Tok.type_npViewArtist_alpha)
    val npViewAlbum = role(Tok.type_npViewAlbum_size, Tok.type_npViewAlbum_style, Tok.type_npViewAlbum_case, Tok.type_npViewAlbum_alpha)
    val npViewTitle = role(Tok.type_npViewTitle_size, Tok.type_npViewTitle_style, Tok.type_npViewTitle_case, Tok.type_npViewTitle_alpha)
    val npViewTime = role(Tok.type_npViewTime_size, Tok.type_npViewTime_style, Tok.type_npViewTime_case, Tok.type_npViewTime_alpha)
    val time = role(Tok.type_time_size, Tok.type_time_style, Tok.type_time_case, Tok.type_time_alpha)
    val toastArtist = role(Tok.type_toastArtist_size, Tok.type_toastArtist_style, Tok.type_toastArtist_case, Tok.type_toastArtist_alpha)
    val toastTitle = role(Tok.type_toastTitle_size, Tok.type_toastTitle_style, Tok.type_toastTitle_case, Tok.type_toastTitle_alpha)
    val saverClock = mixed(Tok.type_saverClock_size, Tok.type_saverClock_style, Tok.type_saverClock_alpha)
    val lockTitle = mixed(Tok.type_lockTitle_size, Tok.type_lockTitle_style, Tok.type_lockTitle_alpha)
    val lockArtist = mixed(Tok.type_lockArtist_size, Tok.type_lockArtist_style, Tok.type_lockArtist_alpha)
    val lockAlbum = mixed(Tok.type_lockAlbum_size, Tok.type_lockAlbum_style, Tok.type_lockAlbum_alpha)
    val overlayNumber = Role(Tok.type_overlayNumber_size, weightOf(Tok.type_overlayNumber_style), Case.Mixed, Tok.overlayLit, Tok.tracking)
    val volNumber = Role(Tok.vol_number, overlayNumber.weight, overlayNumber.case, overlayNumber.alpha, overlayNumber.tracking)
    val volLabel = Role(Tok.vol_label, overlayLabel.weight, overlayLabel.case, overlayLabel.alpha, overlayLabel.tracking)
    val volExit = Role(Tok.vol_exit, overlayLabel.weight, overlayLabel.case, overlayLabel.alpha, overlayLabel.tracking)

    fun npViewNext(b: TokBoard.Board) =
        role(b.type_npViewNext_size, Tok.type_npViewNext_style, Tok.type_npViewNext_case, Tok.type_npViewNext_alpha)

    fun saverFourth(b: TokBoard.Board) =
        role(b.type_saverFourth_size, Tok.type_saverFourth_style, Tok.type_saverFourth_case, Tok.type_saverFourth_alpha)
}
