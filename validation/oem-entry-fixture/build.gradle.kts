// Emulator-only; never install this package on a vehicle.
plugins { id("com.android.application") }
android {
    namespace = "com.wm.launcher"
    compileSdk = 36
    defaultConfig { applicationId = "com.wm.launcher"; minSdk = 23; targetSdk = 23; versionCode = 1; versionName = "entry-fixture" }
}
