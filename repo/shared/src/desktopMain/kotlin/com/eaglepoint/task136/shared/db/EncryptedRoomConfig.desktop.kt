package com.eaglepoint.task136.shared.db

import androidx.room.RoomDatabase
import com.eaglepoint.task136.shared.logging.AppLogger

/**
 * Desktop encryption is not yet implemented.
 * Android uses SQLCipher with Keystore-backed passphrase.
 * Desktop builds should integrate a compatible encryption provider
 * (e.g., SQLCipher JVM binding or Bouncy Castle) before production use.
 *
 * WARNING: Data at rest is unprotected in desktop builds.
 */
actual object EncryptedRoomConfig {
    actual fun apply(builder: RoomDatabase.Builder<AppDatabase>, passphrase: ByteArray): RoomDatabase.Builder<AppDatabase> {
        AppLogger.w("EncryptedRoomConfig", "Desktop database encryption not implemented. Data at rest is unprotected.")
        return builder
    }
}
