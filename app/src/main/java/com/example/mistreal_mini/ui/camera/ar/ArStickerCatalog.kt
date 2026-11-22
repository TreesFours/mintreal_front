package com.example.mistreal_mini.ui.camera.ar

import androidx.compose.ui.graphics.Color

/** Where a sticker is pinned relative to the live tracked face. */
enum class StickerAnchor {
    /** No live tracking needed — a flat color wash over the whole frame. */
    FULL_SCREEN_TINT,
    /** Centered above the face's bounding box, e.g. ears. */
    ABOVE_FACE,
    /** Centered on the eye-to-eye midpoint, width scaled to eye spacing, e.g. glasses. */
    EYES,
    /** Centered on the nose-base landmark, e.g. a puppy nose. */
    NOSE_TIP,
    /**
     * Softens skin texture in the face's bounding-box region. Stills only
     * ([StickerBitmapRenderer] has real pixel access to the captured photo)
     * — the live preview overlay draws on top of a separate PreviewView
     * layer it can't read/blur pixels from (same architectural limit as
     * [com.example.mistreal_mini.util.ar.FaceSwapCompositor]'s live color
     * matching), so selecting this filter changes nothing in the live feed
     * and only takes effect once a photo is captured.
     */
    FACE_SMOOTH,
    /** Centered on the mouth corners' midpoint, width scaled to mouth span, e.g. fangs. */
    MOUTH,
    /**
     * Offset up and to one side of the face, scaled larger than the face
     * itself, so it reads as looming over the user's shoulder rather than
     * sitting on their face — the "something behind you" horror-filter
     * trick. Purely a 2D offset (this overlay has no real depth/occlusion
     * to place anything behind the live camera feed), but convincing
     * enough as a jump-scare-style sticker.
     */
    BEHIND_HEAD
}

/**
 * [tintColor] is now independent of [anchor] — any sticker can combine an
 * anchored emoji with a full-frame color wash (e.g. the holiday filters:
 * a hat/pumpkin emoji above the face plus a seasonal tint), not just the
 * dedicated [StickerAnchor.FULL_SCREEN_TINT] stickers.
 */
data class ArSticker(
    val id: String,
    val name: String,
    val emoji: String,
    val anchor: StickerAnchor,
    val tintColor: Color? = null
)

val AR_STICKER_CATALOG = listOf(
    ArSticker("normal", "Normal", "📷", StickerAnchor.FULL_SCREEN_TINT, null),
    ArSticker("cat_ears", "Cat Ears", "🐱", StickerAnchor.ABOVE_FACE),
    ArSticker("glasses", "Shades", "🕶️", StickerAnchor.EYES),
    ArSticker("puppy", "Puppy Nose", "🐶", StickerAnchor.NOSE_TIP),
    ArSticker("sparkles", "Sparkles", "✨", StickerAnchor.ABOVE_FACE),
    ArSticker("neon", "Neon Glow", "🌈", StickerAnchor.FULL_SCREEN_TINT, Color.Magenta.copy(alpha = 0.18f)),
    ArSticker("retro", "Retro Grain", "🎞️", StickerAnchor.FULL_SCREEN_TINT, Color.DarkGray.copy(alpha = 0.25f)),
    ArSticker("face_smooth", "Smooth Skin", "🧴", StickerAnchor.FACE_SMOOTH),
    ArSticker("christmas", "Christmas", "🎅", StickerAnchor.ABOVE_FACE, Color(0xFF8ED0FF).copy(alpha = 0.12f)),
    ArSticker("snowy", "Snowy", "❄️", StickerAnchor.ABOVE_FACE, Color.White.copy(alpha = 0.10f)),
    ArSticker("halloween", "Halloween", "🎃", StickerAnchor.ABOVE_FACE, Color(0xFFFF7A00).copy(alpha = 0.14f)),
    ArSticker("spooky", "Spooky Ghost", "👻", StickerAnchor.ABOVE_FACE, Color(0xFF5B2A86).copy(alpha = 0.16f)),
    ArSticker("birthday", "Birthday", "🎂", StickerAnchor.ABOVE_FACE, Color(0xFFFF8FD6).copy(alpha = 0.12f)),
    ArSticker("vampire", "Vampire", "🧛", StickerAnchor.MOUTH, Color(0xFF3A0000).copy(alpha = 0.14f)),
    ArSticker("clown_scare", "Clown Scare", "🤡", StickerAnchor.ABOVE_FACE, Color(0xFF8A0000).copy(alpha = 0.16f)),
    ArSticker("shadow_stalker", "Shadow Stalker", "🫥", StickerAnchor.BEHIND_HEAD, Color.Black.copy(alpha = 0.22f)),
    ArSticker("possessed", "Possessed", "😈", StickerAnchor.EYES, Color(0xFF4A0000).copy(alpha = 0.20f))
)
