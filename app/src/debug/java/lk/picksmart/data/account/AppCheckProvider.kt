package lk.picksmart.data.account

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds aren't installed from Play, so they use the debug provider: on first start Firebase
 * logs a debug token, which the owner adds in the Firebase console (`docs/cloud-backup.md`).
 * Never in release builds.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
