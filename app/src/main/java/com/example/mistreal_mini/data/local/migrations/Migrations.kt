package com.example.mistreal_mini.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Index-only — no column/type changes, so this can never fail partway
// through in a way that leaves data in a bad state. Index names must match
// the `name =` given on each entity's @Index annotation exactly, or Room's
// startup schema validation will reject the migration.
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chats_userId_isTrend_trendTitle_timestamp " +
                "ON chats(userId, isTrend, trendTitle, timestamp)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_social_posts_fetchedAt " +
                "ON social_posts(fetchedAt)"
        )
    }
}

// Written explicitly rather than relying on destructive fallback (unlike the
// last couple of additive-table bumps) — wiping a user's entire chat history
// to add one column would be a real loss, not a harmless empty-table reset.
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chats ADD COLUMN revealAt INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS delayed_trends (" +
                "trendTitle TEXT NOT NULL PRIMARY KEY, enabledAt INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chats ADD COLUMN replyToId INTEGER")
    }
}

// ChatEntity never actually persisted an image/video/file attachment at all
// — the columns simply didn't exist, so every attachment silently vanished
// the moment Room's reactive Flow re-emitted the saved row back into the UI
// (ChatMessage.attachmentUrl/attachmentPaths always came back null). Written
// explicitly rather than relying on destructive fallback, same reasoning as
// MIGRATION_18_19.
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chats ADD COLUMN attachmentUrl TEXT")
        db.execSQL("ALTER TABLE chats ADD COLUMN attachmentPathsJson TEXT")
    }
}
